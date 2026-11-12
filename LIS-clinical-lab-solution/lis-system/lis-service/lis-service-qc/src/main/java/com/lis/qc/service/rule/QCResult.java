package com.lis.qc.service.rule;

import lombok.Data;

/**
 * 质控判定结果
 */
@Data
public class QCResult {

    /** 级别: ACCEPT=在控 WARNING=警告(在控但不拒绝) REJECT=失控 */
    public static final String LEVEL_ACCEPT = "ACCEPT";
    public static final String LEVEL_WARNING = "WARNING";
    public static final String LEVEL_REJECT = "REJECT";

    /** 是否接受 (true=接受 WARNING 也视为接受, 不拒绝) */
    private boolean accepted;
    /** 结果级别: ACCEPT / WARNING / REJECT */
    private String level;
    /** 触发的规则名称 */
    private String ruleName;
    /** 失控处理建议 */
    private String suggestion;

    private QCResult() {}

    public static QCResult accept() {
        QCResult r = new QCResult();
        r.setAccepted(true);
        r.setLevel(LEVEL_ACCEPT);
        r.setRuleName("NONE");
        r.setSuggestion("质控在控");
        return r;
    }

    /** 警告 (不拒绝, 质控仍为在控) */
    public static QCResult warning(String rule, String suggestion) {
        QCResult r = new QCResult();
        r.setAccepted(true);
        r.setLevel(LEVEL_WARNING);
        r.setRuleName(rule);
        r.setSuggestion(suggestion);
        return r;
    }

    public static QCResult reject(String rule, String suggestion) {
        QCResult r = new QCResult();
        r.setAccepted(false);
        r.setLevel(LEVEL_REJECT);
        r.setRuleName(rule);
        r.setSuggestion(suggestion);
        return r;
    }
}
