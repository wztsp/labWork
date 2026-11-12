package com.lis.integration.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import javax.sql.DataSource;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * LIS 检验结果回传 HIS (S4010)。
 * <p>
 * 扫描已发布(status=2)且未同步(his_sync_status=0)的报告，按 HIS 平台 4010 报文格式
 * POST 到参数 his.result.url 配置的地址；成功后回写 his_sync_status=1。
 */
@Slf4j
@Service
public class HisResultPushService {

    private static final DateTimeFormatter TS = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final int BATCH_SIZE = 50;

    private final JdbcTemplate jdbcTemplate;
    private final RestTemplate restTemplate = new RestTemplate();
    private final ObjectMapper objectMapper = new ObjectMapper();

    public HisResultPushService(DataSource dataSource) {
        this.jdbcTemplate = new JdbcTemplate(dataSource);
    }

    /**
     * 批量推送待同步报告，返回成功条数
     */
    public int pushPending() {
        if (!"true".equalsIgnoreCase(cfg("his.result.enabled", "true"))) {
            return 0;
        }
        String url = cfg("his.result.url", "");
        if (url.isEmpty()) {
            log.debug("[HIS结果回传] 未配置 his.result.url, 跳过");
            return 0;
        }
        List<Long> ids;
        try {
            ids = jdbcTemplate.queryForList(
                    "SELECT id FROM lis_report WHERE status = 2 AND COALESCE(his_sync_status, 0) <> 1 "
                            + "ORDER BY publish_time ASC LIMIT ?", Long.class, BATCH_SIZE);
        } catch (Exception e) {
            log.error("[HIS结果回传] 查询待同步报告失败", e);
            return 0;
        }
        if (ids.isEmpty()) {
            return 0;
        }
        int success = 0;
        for (Long id : ids) {
            if (pushReport(id, url)) {
                success++;
            }
        }
        log.info("[HIS结果回传] 本次待推送 {} 条, 成功 {}", ids.size(), success);
        return success;
    }

    /**
     * 推送单份报告，用于手工补推
     */
    public boolean pushReport(long reportId) {
        if (!"true".equalsIgnoreCase(cfg("his.result.enabled", "true"))) {
            throw new IllegalStateException("his.result.enabled=false, 结果回传已停用");
        }
        String url = cfg("his.result.url", "");
        if (url.isEmpty()) {
            throw new IllegalStateException("未配置 his.result.url");
        }
        return pushReport(reportId, url);
    }

    private boolean pushReport(long reportId, String url) {
        Map<String, Object> report;
        try {
            List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                    "SELECT r.id, r.report_no, r.sample_id, r.patient_id, r.patient_name, r.report_type, "
                            + "r.report_doctor, r.checker, r.publish_time, r.audit_time, r.pdf_path, "
                            + "(SELECT u.real_name FROM sys_user u WHERE u.id = r.auditor_id) AS auditor_name, "
                            + "(SELECT u.real_name FROM sys_user u WHERE u.id = r.publisher_id) AS publisher_name "
                            + "FROM lis_report r WHERE r.id = ?", reportId);
            if (rows.isEmpty()) {
                log.warn("[HIS结果回传] 报告不存在 id={}", reportId);
                return false;
            }
            report = rows.get(0);
        } catch (Exception e) {
            log.error("[HIS结果回传] 加载报告失败 id={}", reportId, e);
            return false;
        }

        try {
            Map<String, Object> payload = buildPayload(report);
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            String token = cfg("his.result.token", "");
            if (!token.isEmpty()) {
                headers.set("X-HIS-Token", token);
            }
            String json = objectMapper.writeValueAsString(payload);
            ResponseEntity<String> resp = restTemplate.exchange(
                    url, HttpMethod.POST, new HttpEntity<>(json, headers), String.class);
            boolean ok = resp.getStatusCode().is2xxSuccessful() && !isBusinessError(resp.getBody());
            if (ok) {
                jdbcTemplate.update("UPDATE lis_report SET his_sync_status = 1, his_sync_time = ? WHERE id = ?",
                        Timestamp.valueOf(LocalDateTime.now()), reportId);
                log.info("[HIS结果回传] 成功 reportNo={}", report.get("report_no"));
                return true;
            }
            log.warn("[HIS结果回传] 失败 reportNo={}, HTTP={}, body={}", report.get("report_no"),
                    resp.getStatusCode(), truncate(resp.getBody(), 300));
            return false;
        } catch (Exception e) {
            log.error("[HIS结果回传] 调用失败 reportNo={}", report.get("report_no"), e);
            return false;
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> buildPayload(Map<String, Object> report) {
        Long sampleId = longVal(report.get("sample_id"));
        Long patientId = longVal(report.get("patient_id"));

        Map<String, Object> sample = queryOne(
                "SELECT sample_barcode, order_id, visit_id, sample_type, receive_time, collector_id, "
                        + "(SELECT u.real_name FROM sys_user u WHERE u.id = lis_sample.collector_id) AS collector_name "
                        + "FROM lis_sample WHERE id = ?", sampleId);
        Map<String, Object> patient = queryOne(
                "SELECT patient_no, name FROM lis_patient WHERE id = ?", patientId);
        Map<String, Object> visit = sample == null ? null : queryOne(
                "SELECT visit_no FROM lis_visit WHERE id = ?", longVal(sample.get("visit_id")));
        Map<String, Object> order = sample == null ? null : queryOne(
                "SELECT his_apply_id, order_no FROM lis_lab_order WHERE id = ?", longVal(sample.get("order_id")));

        String hisApplyId = order == null ? "" : str(order.get("his_apply_id"));
        String applyId = hisApplyId.contains("#")
                ? hisApplyId.substring(0, hisApplyId.indexOf('#')) : hisApplyId;
        if (applyId.isEmpty() && order != null) {
            applyId = str(order.get("order_no"));
        }

        List<Map<String, Object>> results = sampleId == null ? Collections.emptyList() : jdbcTemplate.queryForList(
                "SELECT r.result_value, r.result_text, r.unit, r.ref_range, r.abnormal_flag, r.is_critical, "
                        + "t.item_code, t.item_name, t.item_name_en, t.short_name "
                        + "FROM lis_result r LEFT JOIN lis_test_item t ON t.id = r.test_item_id "
                        + "WHERE r.sample_id = ? ORDER BY r.id", sampleId);

        List<Map<String, Object>> rptInfo = new ArrayList<>();
        List<String> itemNames = new ArrayList<>();
        int seq = 1;
        for (Map<String, Object> row : results) {
            String value = str(row.get("result_value"));
            if (value.isEmpty()) {
                value = str(row.get("result_text"));
            }
            String itemName = str(row.get("item_name"));
            if (!itemName.isEmpty()) {
                itemNames.add(itemName);
            }
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("seq", String.valueOf(seq++));
            item.put("apply_id", applyId);
            item.put("labitem_code", str(row.get("item_code")));
            item.put("labitem_name", itemName);
            item.put("loitem_id", str(row.get("item_code")));
            item.put("loitem_ename", firstNonBlank(str(row.get("item_name_en")), str(row.get("short_name"))));
            item.put("loitem_cname", itemName);
            item.put("order_rpt_result", value);
            item.put("loitem_unit", str(row.get("unit")));
            item.put("loitem_rv", str(row.get("ref_range")));
            item.put("is_critical", "1".equals(str(row.get("is_critical"))));
            item.put("oaflag", oaflag(str(row.get("abnormal_flag"))));
            item.put("decimals", decimals(value));
            item.put("loitem_result_type", value.matches("-?\\d+(\\.\\d+)?") ? "1" : "2");
            item.put("combin_code", "");
            item.put("ast_list", new ArrayList<>());
            rptInfo.add(item);
        }

        String checker = firstNonBlank(str(report.get("checker")), str(report.get("report_doctor")));
        String auditor = firstNonBlank(str(report.get("auditor_name")), checker);
        String receiver = sample == null ? "" : firstNonBlank(str(sample.get("collector_name")), checker);
        String lspcmNo = lspcmNo();

        Map<String, Object> applyInfo = new LinkedHashMap<>();
        applyInfo.put("rpt_type", "3".equals(str(report.get("report_type"))) ? "1" : "0");
        applyInfo.put("apply_id", applyId);
        applyInfo.put("pat_name", firstNonBlank(str(report.get("patient_name")),
                patient == null ? "" : str(patient.get("name"))));
        applyInfo.put("pid", patient == null ? "" : str(patient.get("patient_no")));
        applyInfo.put("pvid", visit == null ? "" : str(visit.get("visit_no")));
        applyInfo.put("source_system", cfg("his.result.source-system", "ZLHIS"));
        applyInfo.put("rpt_no", str(report.get("report_no")));
        applyInfo.put("spcm_bc_no", sample == null ? "" : str(sample.get("sample_barcode")));
        applyInfo.put("rpt_path", str(report.get("pdf_path")));
        applyInfo.put("lspcm_no", lspcmNo);
        applyInfo.put("chkin_ps", receiver);
        applyInfo.put("chkin_time", sample == null ? time(report.get("audit_time"))
                : time(sample.get("receive_time")));
        applyInfo.put("rptr", str(report.get("report_doctor")));
        applyInfo.put("report_time", time(report.get("publish_time")));
        applyInfo.put("chkr", auditor);
        applyInfo.put("chk_time", time(report.get("audit_time")));
        applyInfo.put("eq_id", "");
        applyInfo.put("notes", "");
        applyInfo.put("pdf_only", "0");
        applyInfo.put("citem_name", String.join(",", itemNames));

        String pdfPath = str(report.get("pdf_path"));
        Map<String, Object> rptFile = new LinkedHashMap<>();
        rptFile.put("lspcm_no", lspcmNo);
        rptFile.put("file_format", "pdf");
        rptFile.put("file_name", pdfPath.isEmpty() ? "" : pdfPath.substring(pdfPath.lastIndexOf('/') + 1));
        rptFile.put("file_content", "");
        rptFile.put("note", "");
        rptFile.put("no_print", "");

        Map<String, Object> input = new LinkedHashMap<>();
        input.put("apply_info", applyInfo);
        input.put("rpt_info", rptInfo);
        input.put("rpt_file", Collections.singletonList(rptFile));
        input.put("rpt_pic", null);
        input.put("head", head());

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("input", input);
        return payload;
    }

    private Map<String, Object> head() {
        Map<String, Object> head = new LinkedHashMap<>();
        head.put("bizno", "S4010");
        head.put("sysno", "DC-SM");
        head.put("tarno", "ZLHIS");
        head.put("time", LocalDateTime.now().format(TS));
        head.put("action_no", UUID.randomUUID().toString());
        return head;
    }

    /**
     * HIS 返回 {"input"/"output":{"ack_info":{"exe_status":"E"}}} 视为业务失败
     */
    private boolean isBusinessError(String body) {
        if (body == null || body.isBlank()) {
            return false;
        }
        try {
            JsonNode root = objectMapper.readTree(body);
            JsonNode ack = root.path("output").path("ack_info");
            if (ack.isMissingNode()) {
                ack = root.path("input").path("ack_info");
            }
            return "E".equalsIgnoreCase(ack.path("exe_status").asText(""));
        } catch (Exception e) {
            log.warn("[HIS结果回传] 应答非 JSON, 按成功处理: {}", truncate(body, 200));
            return false;
        }
    }

    private Map<String, Object> queryOne(String sql, Long id) {
        if (id == null) {
            return null;
        }
        try {
            List<Map<String, Object>> rows = jdbcTemplate.queryForList(sql, id);
            return rows.isEmpty() ? null : rows.get(0);
        } catch (Exception e) {
            log.warn("[HIS结果回传] 查询失败: {}", e.getMessage());
            return null;
        }
    }

    private String cfg(String key, String defaultValue) {
        try {
            List<String> values = jdbcTemplate.queryForList(
                    "SELECT config_value FROM sys_config WHERE config_key = ? LIMIT 1", String.class, key);
            if (values.isEmpty() || values.get(0) == null) {
                return defaultValue;
            }
            return values.get(0).trim();
        } catch (Exception e) {
            return defaultValue;
        }
    }

    /**
     * LIS 异常标志转 HIS: 1-正常 2-偏低 3-偏高 4-阳性(异常) 5-警戒下限 6-警戒上限
     */
    private String oaflag(String abnormalFlag) {
        if (abnormalFlag == null) {
            return "1";
        }
        switch (abnormalFlag.trim().toUpperCase()) {
            case "L": return "2";
            case "H": return "3";
            case "P":
            case "A":
            case "POS":
            case "阳性": return "4";
            default: return "1";
        }
    }

    /**
     * 标本序号必须是大于 0 的数字文本
     */
    private String lspcmNo() {
        String value = cfg("his.result.default-lspcm-no", "1").trim();
        try {
            return Integer.parseInt(value) > 0 ? value : "1";
        } catch (NumberFormatException e) {
            return "1";
        }
    }

    private String decimals(String value) {
        int dot = value.indexOf('.');
        return dot < 0 ? "" : String.valueOf(value.length() - dot - 1);
    }

    private String time(Object value) {
        if (value instanceof Timestamp ts) {
            return ts.toLocalDateTime().format(TS);
        }
        if (value instanceof LocalDateTime dt) {
            return dt.format(TS);
        }
        return "";
    }

    private String str(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }

    private Long longVal(Object value) {
        if (value instanceof Number number) {
            return number.longValue();
        }
        try {
            return value == null ? null : Long.valueOf(String.valueOf(value));
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.trim().isEmpty()) {
                return value;
            }
        }
        return "";
    }

    private String truncate(String value, int maxLength) {
        if (value == null) {
            return "";
        }
        return value.length() > maxLength ? value.substring(0, maxLength) : value;
    }
}
