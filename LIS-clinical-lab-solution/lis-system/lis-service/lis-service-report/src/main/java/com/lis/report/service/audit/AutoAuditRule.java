package com.lis.report.service.audit;

import com.lis.api.vo.ReportVO;

/**
 * 自动审核规则接口 (策略模式)
 * <p>
 * 每条规则实现该接口，由 {@link AutoAuditEngine} 自动收集并按 {@link #priority()} 排序执行。
 */
public interface AutoAuditRule {

    /**
     * 规则名称
     */
    String ruleName();

    /**
     * 优先级 (数字越小越先执行)
     */
    int priority();

    /**
     * 执行规则校验
     */
    AuditResult audit(ReportVO report);

    /**
     * 是否阻断规则: 若为 true 且规则失败，则整批审核不通过
     */
    boolean isBlocking();
}
