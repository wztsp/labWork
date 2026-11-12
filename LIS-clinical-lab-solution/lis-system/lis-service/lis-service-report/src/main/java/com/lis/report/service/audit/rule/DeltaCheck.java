package com.lis.report.service.audit.rule;

import com.lis.api.vo.ReportVO;
import com.lis.api.vo.ResultVO;
import com.lis.report.entity.Result;
import com.lis.report.mapper.ResultMapper;
import com.lis.report.service.audit.AuditResult;
import com.lis.report.service.audit.AutoAuditRule;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Delta 检查规则 (priority=3)
 * <p>
 * 与历史结果比较，变化幅度超过阈值则标记异常。
 * 查询该患者最近一次同类项目已发布报告的结果，计算差值百分比
 * |当前值-历史值| / 历史值 * 100，超过阈值则返回失败。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DeltaCheck implements AutoAuditRule {

    private final ResultMapper resultMapper;

    /** Delta 阈值百分比, 默认 20% (可通过 lis.audit.delta-threshold 配置) */
    @Value("${lis.audit.delta-threshold:20.0}")
    private double deltaThreshold;

    @Override
    public String ruleName() {
        return "DeltaCheck";
    }

    @Override
    public int priority() {
        return 3;
    }

    @Override
    public boolean isBlocking() {
        return false;
    }

    @Override
    public AuditResult audit(ReportVO report) {
        Long patientId = report.getPatientId();
        Long currentSampleId = report.getSampleId();
        if (patientId == null || currentSampleId == null) {
            log.debug("Delta 检查跳过: 缺少 patientId 或 sampleId, reportId={}", report.getId());
            return AuditResult.pass(ruleName());
        }
        List<ResultVO> results = report.getResults();
        if (results == null || results.isEmpty()) {
            return AuditResult.pass(ruleName());
        }

        List<String> failedItems = new ArrayList<>();
        for (ResultVO r : results) {
            if (r.getResultValue() == null || r.getTestItemId() == null) {
                continue;
            }
            double currentValue;
            try {
                currentValue = Double.parseDouble(r.getResultValue().trim());
            } catch (NumberFormatException ignore) {
                // 非数字结果不参与 delta 检查
                continue;
            }

            Result history = resultMapper.selectLastPublishedByPatientAndItem(
                    patientId, r.getTestItemId(), currentSampleId);
            if (history == null || history.getResultValue() == null) {
                // 无历史结果，跳过
                continue;
            }
            double historyValue;
            try {
                historyValue = Double.parseDouble(history.getResultValue().trim());
            } catch (NumberFormatException ignore) {
                continue;
            }
            // 历史值为 0 时无法计算百分比，跳过避免除零
            if (historyValue == 0) {
                continue;
            }
            double deltaPercent = Math.abs(currentValue - historyValue) / Math.abs(historyValue) * 100.0;
            if (deltaPercent > deltaThreshold) {
                failedItems.add(r.getTestItemCode() + ": " + historyValue + " -> " + currentValue
                        + " (变化 " + String.format("%.1f", deltaPercent) + "%)");
                log.warn("Delta 检查失败: item={}, history={}, current={}, delta={}%, threshold={}%",
                        r.getTestItemCode(), historyValue, currentValue, deltaPercent, deltaThreshold);
            }
        }

        if (failedItems.isEmpty()) {
            log.debug("Delta 检查通过: reportId={}", report.getId());
            return AuditResult.pass(ruleName());
        }
        return AuditResult.fail(ruleName(),
                "Delta检查失败: 变化超过阈值 " + deltaThreshold + "% - " + String.join("; ", failedItems));
    }
}
