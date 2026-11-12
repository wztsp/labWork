package com.lis.report.service.audit;

import lombok.Data;

/**
 * 审核结果
 */
@Data
public class AuditResult {

    private boolean passed;
    private String ruleName;
    private String message;

    private AuditResult() {}

    public static AuditResult pass() {
        AuditResult r = new AuditResult();
        r.setPassed(true);
        r.setRuleName("ALL");
        r.setMessage("审核通过");
        return r;
    }

    public static AuditResult pass(String ruleName) {
        AuditResult r = new AuditResult();
        r.setPassed(true);
        r.setRuleName(ruleName);
        r.setMessage("规则通过");
        return r;
    }

    public static AuditResult fail(String rule, String msg) {
        AuditResult r = new AuditResult();
        r.setPassed(false);
        r.setRuleName(rule);
        r.setMessage(msg);
        return r;
    }
}
