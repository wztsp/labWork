package com.lis.integration.job;

import com.lis.integration.adapter.HisAdapter;
import com.lis.integration.adapter.HisAdapterFactory;
import com.lis.integration.dto.LabReport;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 结果回传定时任务
 * <p>
 * 每 5 分钟扫描已发布但未同步到 HIS 的报告 (lis_report.his_sync_status = 0 且 status = 2)，
 * 转换为 {@link LabReport} 后通过 HIS 适配器回传 (HL7 ORU^R01 或 REST POST)。
 * 同步成功后更新 his_sync_status = 1; 若 HIS 接口未配置或调用失败, 记录日志并跳过, 等待下次重试。
 */
@Slf4j
@Component
public class ResultSyncJob {

    private final HisAdapterFactory adapterFactory;
    private final JdbcTemplate jdbcTemplate;

    @Value("${integration.his.default-vendor:UNIVERSAL_REST}")
    private String defaultVendor;

    /**
     * HIS 结果回传开关 (false 时仅记录日志, 不实际调用 HIS)
     */
    @Value("${integration.sync.result-enabled:true}")
    private boolean resultSyncEnabled;

    /**
     * 单次回传报告数量上限, 避免一次性推送过多
     */
    @Value("${integration.sync.result-batch-size:50}")
    private int batchSize;

    @Autowired
    public ResultSyncJob(HisAdapterFactory adapterFactory, DataSource dataSource) {
        this.adapterFactory = adapterFactory;
        this.jdbcTemplate = new JdbcTemplate(dataSource);
    }

    /**
     * 每 5 分钟执行一次
     */
    @Scheduled(cron = "${integration.sync.result-cron:0 */5 * * * ?}")
    public void syncResults() {
        if (!resultSyncEnabled) {
            log.debug("[ResultSyncJob] 结果回传已禁用 (integration.sync.result-enabled=false)");
            return;
        }
        log.info("[ResultSyncJob] 开始回传未同步报告...");
        HisAdapter adapter;
        try {
            adapter = adapterFactory.getAdapter(defaultVendor);
        } catch (Exception e) {
            log.warn("[ResultSyncJob] HIS 适配器未配置或不可用, 跳过本次同步: {}", e.getMessage());
            return;
        }

        List<ReportRow> pending = queryUnsyncedReports();
        if (pending.isEmpty()) {
            log.info("[ResultSyncJob] 无待同步报告");
            return;
        }

        int success = 0;
        int fail = 0;
        for (ReportRow row : pending) {
            try {
                LabReport report = buildLabReport(row);
                boolean ok = adapter.sendResults(report);
                if (ok) {
                    markSynced(row.id);
                    success++;
                } else {
                    fail++;
                    log.warn("[ResultSyncJob] 报告回传失败 (HIS 返回失败): reportNo={}", row.reportNo);
                }
            } catch (Exception e) {
                fail++;
                log.error("[ResultSyncJob] 报告回传异常: reportNo={}", row.reportNo, e);
            }
        }
        log.info("[ResultSyncJob] 回传完成: 成功 {} / 失败 {} / 总 {} ", success, fail, pending.size());
    }

    private List<ReportRow> queryUnsyncedReports() {
        String sql = "SELECT id, report_no, sample_id, patient_id, patient_name, report_type, "
                + "report_doctor, checker, publish_time "
                + "FROM lis_report WHERE status = 2 AND his_sync_status = 0 "
                + "ORDER BY publish_time ASC LIMIT ?";
        try {
            return jdbcTemplate.query(sql,
                    (rs, rowNum) -> {
                        ReportRow r = new ReportRow();
                        r.id = rs.getLong("id");
                        r.reportNo = rs.getString("report_no");
                        r.sampleId = rs.getLong("sample_id");
                        r.patientId = rs.getString("patient_id");
                        r.patientName = rs.getString("patient_name");
                        r.reportType = rs.getString("report_type");
                        r.reportDoctor = rs.getString("report_doctor");
                        r.checker = rs.getString("checker");
                        Timestamp ts = rs.getTimestamp("publish_time");
                        r.publishTime = ts == null ? null : ts.toLocalDateTime();
                        return r;
                    }, batchSize);
        } catch (Exception e) {
            log.error("[ResultSyncJob] 查询待同步报告失败", e);
            return Collections.emptyList();
        }
    }

    private LabReport buildLabReport(ReportRow row) {
        List<LabReport.ResultItem> results = loadResultItems(row.sampleId);
        String reportType = row.reportType == null ? null
                : mapReportType(row.reportType);
        return LabReport.builder()
                .reportNo(row.reportNo)
                .sampleId(row.sampleId)
                .patientId(row.patientId)
                .patientName(row.patientName)
                .reportType(reportType)
                .reportDoctor(row.reportDoctor)
                .checker(row.checker)
                .publishTime(row.publishTime)
                .results(results)
                .build();
    }

    private List<LabReport.ResultItem> loadResultItems(Long sampleId) {
        String sql = "SELECT r.result_value, r.result_text, r.unit, r.ref_range, r.abnormal_flag, "
                + "t.item_code, t.item_name "
                + "FROM lis_result r LEFT JOIN lis_test_item t ON r.test_item_id = t.id "
                + "WHERE r.sample_id = ?";
        try {
            return jdbcTemplate.query(sql,
                    (rs, rowNum) -> LabReport.ResultItem.builder()
                            .itemCode(rs.getString("item_code"))
                            .itemName(rs.getString("item_name"))
                            .resultValue(rs.getString("result_value") != null
                                    ? rs.getString("result_value")
                                    : rs.getString("result_text"))
                            .unit(rs.getString("unit"))
                            .refRange(rs.getString("ref_range"))
                            .abnormalFlag(rs.getString("abnormal_flag"))
                            .build(),
                    sampleId);
        } catch (Exception e) {
            log.error("[ResultSyncJob] 加载报告结果项失败 sampleId={}", sampleId, e);
            return new ArrayList<>();
        }
    }

    private void markSynced(Long reportId) {
        try {
            jdbcTemplate.update(
                    "UPDATE lis_report SET his_sync_status = 1, his_sync_time = ? WHERE id = ?",
                    Timestamp.valueOf(LocalDateTime.now()), reportId);
        } catch (Exception e) {
            log.error("[ResultSyncJob] 更新同步状态失败 reportId={}", reportId, e);
        }
    }

    private String mapReportType(String type) {
        switch (type) {
            case "1": return "ROUTINE";
            case "2": return "URGENT";
            case "3": return "MICRO";
            default: return type;
        }
    }

    /** 内部报告行 */
    private static class ReportRow {
        Long id;
        String reportNo;
        Long sampleId;
        String patientId;
        String patientName;
        String reportType;
        String reportDoctor;
        String checker;
        LocalDateTime publishTime;
    }
}
