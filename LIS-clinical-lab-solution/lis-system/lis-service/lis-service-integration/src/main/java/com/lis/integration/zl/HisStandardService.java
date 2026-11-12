package com.lis.integration.zl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.lis.integration.service.HisResultPushService;
import com.lis.sample.service.HisApplyService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import javax.sql.DataSource;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 中联新标准服务(检验)业务实现。
 * <p>
 * 服务清单:
 * S1001 人员信息 / S1002 部门信息 / S1003 部门人员 / S1008 诊疗项目字典 / S1009 收费项目字典 /
 * S3001 生成费用单 / S3002 删除费用单 / S3031 查询收费状态 / S3103 危急值回传 /
 * S3123 批量查询医嘱 / S4009 申请状态回写 / S4010 报告回传 / S4011 取消报告 / S4020 批量查询申请。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class HisStandardService {

    private static final DateTimeFormatter TS = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final ZlHisClient client;
    private final JdbcTemplate jdbcTemplate;
    private final HisApplyService hisApplyService;
    private final HisResultPushService hisResultPushService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    // ==================== HIS 查询/字典/费用服务 ====================

    public JsonNode staff(Map<String, Object> req) {
        return client.call("S1001", Collections.singletonMap("req_info", safe(req)));
    }

    public JsonNode depts(Map<String, Object> req) {
        return client.call("S1002", Collections.singletonMap("req_info", safe(req)));
    }

    public JsonNode deptStaff(Map<String, Object> req) {
        return client.call("S1003", Collections.singletonMap("req_info", safe(req)));
    }

    public JsonNode dictItems(Map<String, Object> req) {
        return client.call("S1008", Collections.singletonMap("req_info", safe(req)));
    }

    public JsonNode dictFees(Map<String, Object> req) {
        return client.call("S1009", Collections.singletonMap("req_info", safe(req)));
    }

    public JsonNode feeStatus(Map<String, Object> req) {
        return client.call("S3031", Collections.singletonMap("req_info", safe(req)));
    }

    public JsonNode patientOrders(Map<String, Object> req) {
        return client.call("S3123", Collections.singletonMap("req_info", safe(req)));
    }

    /**
     * S3001 生成费用划价（记帐）单
     */
    public JsonNode feeCreate(JsonNode body) {
        return client.call("S3001", Collections.singletonMap("fee_info", feeList(body)));
    }

    /**
     * S3002 删除费用划价（记帐）单
     */
    public JsonNode feeDelete(JsonNode body) {
        return client.call("S3002", Collections.singletonMap("fee_info", feeList(body)));
    }

    // ==================== S4009 检验申请状态回写 ====================

    /**
     * 手动回写单条标本状态
     */
    public boolean pushSampleStatus(long sampleId) {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                "SELECT id, status, sample_barcode, collector_id, reject_person, reject_type, reject_reason "
                        + "FROM lis_sample WHERE id = ?", sampleId);
        if (rows.isEmpty()) {
            return false;
        }
        return doPushSampleStatus(rows.get(0));
    }

    /**
     * 定时扫描状态有变化的标本并回写 HIS
     */
    public int pushPendingSampleStatus() {
        if (!client.enabled()) {
            return 0;
        }
        List<Map<String, Object>> rows;
        try {
            rows = jdbcTemplate.queryForList(
                    "SELECT s.id, s.status, s.sample_barcode, s.collector_id, s.reject_person, s.reject_type, "
                            + "s.reject_reason FROM lis_sample s "
                            + "LEFT JOIN lis_his_sample_status_push p ON p.sample_id = s.id "
                            + "LEFT JOIN lis_lab_order l ON l.id = s.order_id "
                            + "WHERE COALESCE(s.deleted, 0) = 0 AND s.status IN (0,1,2,3,4,6,9) "
                            + "AND (COALESCE(l.his_apply_id, '') <> '' OR EXISTS ("
                            + "  SELECT 1 FROM lis_sample_order_item soi JOIN lis_lab_order l2 ON l2.id = soi.order_id "
                            + "  WHERE soi.sample_id = s.id AND COALESCE(l2.his_apply_id, '') <> '')) "
                            + "AND (p.sample_id IS NULL OR p.last_status IS DISTINCT FROM s.status) "
                            + "AND (p.retry_count IS NULL OR p.retry_count < 20) "
                            + "ORDER BY s.update_time ASC LIMIT 30");
        } catch (Exception e) {
            log.warn("[S4009] 扫描标本状态失败: {}", e.getMessage());
            return 0;
        }
        int success = 0;
        for (Map<String, Object> row : rows) {
            if (doPushSampleStatus(row)) {
                success++;
            }
        }
        if (!rows.isEmpty()) {
            log.info("[S4009] 本次待回写 {} 条, 成功 {}", rows.size(), success);
        }
        return success;
    }

    private boolean doPushSampleStatus(Map<String, Object> row) {
        Long sampleId = longVal(row.get("id"));
        Integer status = intVal(row.get("status"));
        String oprtrType = oprtrType(status);
        if (oprtrType.isEmpty()) {
            return false;
        }
        String barcode = str(row.get("sample_barcode"));
        try {
            Map<String, Object> apply = new LinkedHashMap<>();
            apply.put("system", client.cfg("his.zl.sysno", "LIS"));
            apply.put("apply_id", hisApplyIds(sampleId));
            apply.put("oprtr_type", oprtrType);
            apply.put("spcm_bc_no", barcode);
            apply.put("oprtr_name", operatorName(row));
            apply.put("organization_id", client.cfg("his.zl.org-id", "0"));
            apply.put("organization_code", client.cfg("his.zl.org-code", "LIS"));
            apply.put("organization_name", client.cfg("his.zl.org-name", "检验科"));
            apply.put("oprtr_time", LocalDateTime.now().format(TS));
            boolean rejected = status != null && status == 9;
            apply.put("rejection_no", rejected ? "1" : "");
            apply.put("rejection_content", rejected ? str(row.get("reject_reason")) : "");
            apply.put("rejection_note", rejected ? str(row.get("reject_type")) : "");
            apply.put("verify_chrg", "");
            apply.put("submission", "");
            apply.put("submission_time", "");
            apply.put("eq_id", "");
            apply.put("eq_name", "");
            apply.put("lspcm_no", "");
            apply.put("note", "LIS状态回写");
            client.call("S4009", Collections.singletonMap("apply_info",
                    Collections.singletonList(apply)));
            markSampleStatus(sampleId, status, true, "");
            return true;
        } catch (Exception e) {
            log.warn("[S4009] 标本 {} 回写失败: {}", barcode, e.getMessage());
            markSampleStatus(sampleId, status, false, e.getMessage());
            return false;
        }
    }

    private void markSampleStatus(Long sampleId, Integer status, boolean success, String error) {
        Timestamp now = Timestamp.valueOf(LocalDateTime.now());
        try {
            if (success) {
                jdbcTemplate.update(
                        "INSERT INTO lis_his_sample_status_push (sample_id, last_status, last_pushed_status, "
                                + "retry_count, error_msg, last_push_time, update_time) VALUES (?,?,?,0,'',?,?) "
                                + "ON CONFLICT (sample_id) DO UPDATE SET last_status = EXCLUDED.last_status, "
                                + "last_pushed_status = EXCLUDED.last_pushed_status, retry_count = 0, "
                                + "error_msg = '', last_push_time = EXCLUDED.last_push_time, "
                                + "update_time = EXCLUDED.update_time",
                        sampleId, status, status, now, now);
            } else {
                jdbcTemplate.update(
                        "INSERT INTO lis_his_sample_status_push (sample_id, last_status, last_pushed_status, "
                                + "retry_count, error_msg, update_time) VALUES (?,?,NULL,1,?,?) "
                                + "ON CONFLICT (sample_id) DO UPDATE SET retry_count = "
                                + "lis_his_sample_status_push.retry_count + 1, error_msg = EXCLUDED.error_msg, "
                                + "update_time = EXCLUDED.update_time",
                        sampleId, status, truncate(error, 500), now);
            }
        } catch (Exception e) {
            log.warn("[S4009] 记录回写状态失败 sampleId={}: {}", sampleId, e.getMessage());
        }
    }

    // ==================== S3103 危急值回传 ====================

    /**
     * 手动回传单条危急值结果
     */
    public boolean pushCritical(long resultId) {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(criticalSql("r.id = ?"), resultId);
        return !rows.isEmpty() && doPushCritical(rows.get(0));
    }

    /**
     * 定时扫描未回传的危急值结果
     */
    public int pushPendingCritical() {
        if (!client.enabled()) {
            return 0;
        }
        List<Map<String, Object>> rows;
        try {
            rows = jdbcTemplate.queryForList(
                    "SELECT r.id, r.result_value, r.result_text, r.unit, r.ref_range, t.item_name, "
                            + "s.id AS sample_id, s.visit_id, l.his_apply_id, l.order_no, v.visit_type, "
                            + "COALESCE((SELECT u.real_name FROM sys_user u WHERE u.id = r.auditor_id), '系统') AS chkr "
                            + "FROM lis_result r "
                            + "JOIN lis_sample s ON s.id = r.sample_id "
                            + "LEFT JOIN lis_visit v ON v.id = s.visit_id "
                            + "LEFT JOIN lis_test_item t ON t.id = r.test_item_id "
                            + "LEFT JOIN lis_lab_order l ON l.id = s.order_id "
                            + "LEFT JOIN lis_his_critical_push p ON p.result_id = r.id "
                            + "WHERE r.is_critical = 1 AND p.result_id IS NULL "
                            + "AND COALESCE(l.his_apply_id, '') <> '' "
                            + "ORDER BY r.id ASC LIMIT 20");
        } catch (Exception e) {
            log.warn("[S3103] 扫描危急值失败: {}", e.getMessage());
            return 0;
        }
        int success = 0;
        for (Map<String, Object> row : rows) {
            if (doPushCritical(row)) {
                success++;
            }
        }
        if (!rows.isEmpty()) {
            log.info("[S3103] 本次待回传 {} 条, 成功 {}", rows.size(), success);
        }
        return success;
    }

    private String criticalSql(String condition) {
        return "SELECT r.id, r.result_value, r.result_text, r.unit, r.ref_range, t.item_name, "
                + "s.id AS sample_id, s.visit_id, l.his_apply_id, l.order_no, v.visit_type, "
                + "COALESCE((SELECT u.real_name FROM sys_user u WHERE u.id = r.auditor_id), '系统') AS chkr "
                + "FROM lis_result r JOIN lis_sample s ON s.id = r.sample_id "
                + "LEFT JOIN lis_visit v ON v.id = s.visit_id "
                + "LEFT JOIN lis_test_item t ON t.id = r.test_item_id "
                + "LEFT JOIN lis_lab_order l ON l.id = s.order_id WHERE " + condition;
    }

    private boolean doPushCritical(Map<String, Object> row) {
        Long resultId = longVal(row.get("id"));
        String value = firstNonBlank(str(row.get("result_value")), str(row.get("result_text")));
        String description = str(row.get("item_name")) + " " + value
                + (str(row.get("unit")).isEmpty() ? "" : " " + str(row.get("unit")))
                + (str(row.get("ref_range")).isEmpty() ? "" : " 参考:" + str(row.get("ref_range")));
        try {
            Map<String, Object> critical = new LinkedHashMap<>();
            critical.put("critical_type", "1");
            critical.put("order_id", hisOrderId(row));
            critical.put("data_source", client.cfg("his.zl.sysno", "LIS"));
            critical.put("rpt_id", "");
            critical.put("rptexedept_id", client.cfg("his.zl.rpt-dept-id", "0"));
            critical.put("chk_time", LocalDateTime.now().format(TS));
            critical.put("chkr", firstNonBlank(str(row.get("chkr")), "系统"));
            critical.put("order_rpt_result", description.trim());
            critical.put("critical_id", "");
            critical.put("treatment_situation", "");
            critical.put("confirm_chkr", "");
            critical.put("confirm_time", "");
            critical.put("confirm_deptid", "");
            critical.put("is_critical", "1");

            Map<String, Object> params = new LinkedHashMap<>();
            params.put("pat_visit_type", intVal(row.get("visit_type")) != null
                    && intVal(row.get("visit_type")) == 2 ? "2" : "1");
            params.put("critical_info", Collections.singletonList(critical));
            JsonNode resp = client.call("S3103", params);

            String criticalId = "";
            JsonNode info = resp.path("input").path("critical_info");
            if (info.isArray() && !info.isEmpty()) {
                criticalId = info.get(0).path("critical_id").asText("");
            }
            jdbcTemplate.update(
                    "INSERT INTO lis_his_critical_push (result_id, critical_id, push_status, retry_count, "
                            + "create_time, update_time) VALUES (?,?,1,0,?,?) "
                            + "ON CONFLICT (result_id) DO UPDATE SET critical_id = EXCLUDED.critical_id, "
                            + "push_status = 1, error_msg = '', update_time = EXCLUDED.update_time",
                    resultId, criticalId, Timestamp.valueOf(LocalDateTime.now()),
                    Timestamp.valueOf(LocalDateTime.now()));
            return true;
        } catch (Exception e) {
            log.warn("[S3103] 危急值 resultId={} 回传失败: {}", resultId, e.getMessage());
            try {
                jdbcTemplate.update(
                        "INSERT INTO lis_his_critical_push (result_id, push_status, retry_count, error_msg, "
                                + "update_time) VALUES (?,0,1,?,?) ON CONFLICT (result_id) DO UPDATE SET "
                                + "retry_count = lis_his_critical_push.retry_count + 1, "
                                + "error_msg = EXCLUDED.error_msg, update_time = EXCLUDED.update_time",
                        resultId, truncate(e.getMessage(), 500), Timestamp.valueOf(LocalDateTime.now()));
            } catch (Exception ignore) {
                // 记录失败不影响主流程
            }
            return false;
        }
    }

    // ==================== S4010 / S4011 报告 ====================

    /**
     * S4010 手动回传单份报告
     */
    public boolean pushReport(long reportId) {
        return hisResultPushService.pushReport(reportId);
    }

    /**
     * S4011 取消检验报告
     */
    public boolean cancelReport(long reportId) {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                "SELECT r.id, r.report_no, s.sample_barcode, l.his_apply_id, l.order_no "
                        + "FROM lis_report r LEFT JOIN lis_sample s ON s.id = r.sample_id "
                        + "LEFT JOIN lis_lab_order l ON l.id = s.order_id WHERE r.id = ?", reportId);
        if (rows.isEmpty()) {
            return false;
        }
        Map<String, Object> row = rows.get(0);
        Map<String, Object> apply = new LinkedHashMap<>();
        apply.put("apply_id", applyIdOf(row));
        apply.put("spcm_bc_no", str(row.get("sample_barcode")));
        apply.put("oprtr_name", client.cfg("his.zl.operator", "系统"));
        apply.put("oprtr_time", LocalDateTime.now().format(TS));
        client.call("S4011", Collections.singletonMap("apply_info", apply));
        jdbcTemplate.update("UPDATE lis_report SET his_sync_status = 2, his_sync_time = ? WHERE id = ?",
                Timestamp.valueOf(LocalDateTime.now()), reportId);
        log.info("[S4011] 报告 {} 取消回传成功", row.get("report_no"));
        return true;
    }

    /**
     * 定时扫描"已回传过、但现在不是已发布状态"的报告，推送 S4011
     */
    public int pushPendingReportCancel() {
        if (!client.enabled()) {
            return 0;
        }
        List<Long> ids;
        try {
            ids = jdbcTemplate.queryForList(
                    "SELECT id FROM lis_report WHERE COALESCE(his_sync_status, 0) = 1 AND status <> 2 "
                            + "ORDER BY update_time ASC LIMIT 20", Long.class);
        } catch (Exception e) {
            log.warn("[S4011] 扫描待取消报告失败: {}", e.getMessage());
            return 0;
        }
        int success = 0;
        for (Long id : ids) {
            try {
                if (cancelReport(id)) {
                    success++;
                }
            } catch (Exception e) {
                log.warn("[S4011] 报告 {} 取消回传失败: {}", id, e.getMessage());
            }
        }
        return success;
    }

    // ==================== S4020 批量查询检验申请并入库 ====================

    /**
     * 查询 HIS 检验申请(原始应答)
     */
    public JsonNode queryApplies(Map<String, Object> req) {
        return client.call("S4020", Collections.singletonMap("req_info", safe(req)));
    }

    /**
     * 查询 HIS 检验申请并导入 LIS 待采集任务，返回导入条数
     */
    public int pullApplies(Map<String, Object> req) {
        JsonNode resp = queryApplies(req);
        JsonNode arr = resp.path("input").path("apply_info");
        if (!arr.isArray()) {
            return 0;
        }
        int imported = 0;
        for (JsonNode apply : arr) {
            try {
                JsonNode payload = toReceivePayload(apply);
                Map<String, Object> result = hisApplyService.receive(payload, client.cfg("his.apply.token", ""));
                if ("A".equalsIgnoreCase(ackStatus(result))) {
                    imported++;
                }
            } catch (Exception e) {
                log.warn("[S4020] 申请 {} 导入失败: {}", apply.path("apply_id").asText(""), e.getMessage());
            }
        }
        log.info("[S4020] 查询到 {} 条申请, 导入 {} 条", arr.size(), imported);
        return imported;
    }

    /**
     * 将 S4020 的单条申请转换为 4011 接收报文，复用既有落库逻辑
     */
    private JsonNode toReceivePayload(JsonNode apply) {
        ObjectNode root = objectMapper.createObjectNode();
        ObjectNode input = root.putObject("input");

        ObjectNode pv1 = input.putObject("pv1_info");
        copy(apply, pv1, "pid");
        copy(apply, pv1, "pat_name");
        copy(apply, pv1, "pat_sex");
        copy(apply, pv1, "pat_age");
        copy(apply, pv1, "pat_brsdate");
        copy(apply, pv1, "outpno");
        copy(apply, pv1, "inpno");
        copy(apply, pv1, "pvid");
        copy(apply, pv1, "dept_name");
        copy(apply, pv1, "inp_ward_name");
        copy(apply, pv1, "inp_bed_no");
        copy(apply, pv1, "pat_visit_type");

        ObjectNode head = input.putObject("head");
        head.put("bizno", "S4020");
        head.put("action_no", "S4020-" + strNode(apply, "apply_id"));

        ArrayNode applies = input.putArray("apply_info");
        ObjectNode item = applies.addObject();
        copy(apply, item, "apply_id");
        copy(apply, item, "apply_time");
        copy(apply, item, "apply_dept_name");
        copy(apply, item, "placer_name");
        copy(apply, item, "lspcm_name");
        copy(apply, item, "order_content");
        ArrayNode items = item.putArray("apply_item");
        for (JsonNode applyItem : apply.path("apply_item")) {
            ObjectNode target = items.addObject();
            copy(applyItem, target, "order_id");
            copy(applyItem, target, "citem_id");
            copy(applyItem, target, "citem_name");
            copy(applyItem, target, "citem_code");
        }

        ArrayNode dgs = input.putArray("dg1_info");
        for (JsonNode dg : apply.path("dg1_info")) {
            ObjectNode target = dgs.addObject();
            copy(dg, target, "dz_content");
            copy(dg, target, "dz_code");
            copy(dg, target, "dz_name");
        }
        return root;
    }

    private void copy(JsonNode source, ObjectNode target, String field) {
        JsonNode value = source.path(field);
        if (!value.isMissingNode() && !value.isNull()) {
            target.set(field, value);
        }
    }

    @SuppressWarnings("unchecked")
    private String ackStatus(Map<String, Object> result) {
        if (result == null) {
            return "";
        }
        Object output = result.get("output");
        if (!(output instanceof Map<?, ?> out)) {
            return "";
        }
        Object ack = out.get("ack_info");
        if (!(ack instanceof Map<?, ?> ackInfo)) {
            return "";
        }
        Object status = ackInfo.get("exe_status");
        return status == null ? "" : String.valueOf(status);
    }

    // ==================== 工具方法 ====================

    private List<Map<String, Object>> feeList(JsonNode body) {
        if (body == null || body.isNull()) {
            return Collections.emptyList();
        }
        JsonNode node = body.has("fee_info") ? body.get("fee_info") : body;
        if (node.isArray()) {
            return objectMapper.convertValue(node, List.class);
        }
        if (node.isObject()) {
            return Collections.singletonList(objectMapper.convertValue(node, Map.class));
        }
        return Collections.emptyList();
    }

    private Map<String, Object> safe(Map<String, Object> req) {
        return req == null ? new LinkedHashMap<>() : req;
    }

    private String oprtrType(Integer status) {
        if (status == null) {
            return "";
        }
        switch (status) {
            case 0: return "1";
            case 1: return "2";
            case 2:
            case 3: return "5";
            case 4: return "6";
            case 6: return "8";
            case 9: return "12";
            default: return "";
        }
    }

    private String hisApplyIds(Long sampleId) {
        try {
            List<String> ids = jdbcTemplate.queryForList(
                    "SELECT DISTINCT l.his_apply_id FROM lis_lab_order l "
                            + "WHERE l.id = (SELECT order_id FROM lis_sample WHERE id = ?) "
                            + "OR l.id IN (SELECT order_id FROM lis_sample_order_item WHERE sample_id = ?)",
                    String.class, sampleId, sampleId);
            List<String> applies = new ArrayList<>();
            for (String id : ids) {
                if (id == null || id.isEmpty()) {
                    continue;
                }
                String applyId = id.contains("#") ? id.substring(0, id.indexOf('#')) : id;
                if (!applies.contains(applyId)) {
                    applies.add(applyId);
                }
            }
            return String.join(",", applies);
        } catch (Exception e) {
            return "";
        }
    }

    private String hisOrderId(Map<String, Object> row) {
        String hisApplyId = str(row.get("his_apply_id"));
        if (hisApplyId.contains("#")) {
            return hisApplyId.substring(hisApplyId.indexOf('#') + 1);
        }
        return firstNonBlank(hisApplyId, str(row.get("order_no")));
    }

    private String applyIdOf(Map<String, Object> row) {
        String hisApplyId = str(row.get("his_apply_id"));
        if (hisApplyId.contains("#")) {
            return hisApplyId.substring(0, hisApplyId.indexOf('#'));
        }
        return firstNonBlank(hisApplyId, str(row.get("order_no")));
    }

    private String operatorName(Map<String, Object> row) {
        Long collectorId = longVal(row.get("collector_id"));
        if (collectorId != null) {
            try {
                List<String> names = jdbcTemplate.queryForList(
                        "SELECT real_name FROM sys_user WHERE id = ? LIMIT 1", String.class, collectorId);
                if (!names.isEmpty() && names.get(0) != null) {
                    return names.get(0);
                }
            } catch (Exception ignore) {
                // 忽略
            }
        }
        return firstNonBlank(str(row.get("reject_person")), "系统");
    }

    private String strNode(JsonNode node, String field) {
        return node.path(field).asText("");
    }

    private String str(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }

    private Integer intVal(Object value) {
        if (value instanceof Number number) {
            return number.intValue();
        }
        try {
            return value == null ? null : Integer.valueOf(String.valueOf(value));
        } catch (NumberFormatException e) {
            return null;
        }
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
