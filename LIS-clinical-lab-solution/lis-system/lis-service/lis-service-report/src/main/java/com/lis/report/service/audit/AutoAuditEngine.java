package com.lis.report.service.audit;

import com.lis.api.vo.ReportVO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.Comparator;
import java.util.List;

/**
 * 自动审核引擎
 * <p>
 * 持有所有 {@link AutoAuditRule} 实现 (由 Spring 自动注入)。
 * 按 priority 排序逐条执行，任一 blocking 规则失败则整批不通过。
 */
@Slf4j
@Component
public class AutoAuditEngine {

    private final List<AutoAuditRule> rules;

    @Autowired
    public AutoAuditEngine(List<AutoAuditRule> rules) {
        // 启动时按 priority 升序排序
        this.rules = rules.stream()
                .sorted(Comparator.comparingInt(AutoAuditRule::priority))
                .toList();
        log.info("自动审核引擎加载规则 {} 条", this.rules.size());
        this.rules.forEach(r -> log.info("  规则: {} priority={} blocking={}",
                r.ruleName(), r.priority(), r.isBlocking()));
    }

    /**
     * 执行整批规则评估
     */
    public AuditResult evaluate(ReportVO report) {
        if (report == null || report.getResults() == null || report.getResults().isEmpty()) {
            return AuditResult.fail("PreCheck", "报告或结果为空，无法审核");
        }
        for (AutoAuditRule rule : rules) {
            AuditResult result;
            try {
                result = rule.audit(report);
            } catch (Exception e) {
                log.error("规则 {} 执行异常", rule.ruleName(), e);
                result = AuditResult.fail(rule.ruleName(), "规则执行异常: " + e.getMessage());
            }
            log.debug("规则 [{}] 评估结果: passed={} msg={}", rule.ruleName(),
                    result.isPassed(), result.getMessage());
            if (!result.isPassed() && rule.isBlocking()) {
                log.warn("阻断规则 [{}] 失败，整批审核不通过: {}", rule.ruleName(), result.getMessage());
                return result;
            }
        }
        return AuditResult.pass();
    }
}
