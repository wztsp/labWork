package com.lis.report.service.audit.rule;

import com.lis.api.vo.ReportVO;
import com.lis.api.vo.ResultVO;
import com.lis.report.service.audit.AuditResult;
import com.lis.report.service.audit.AutoAuditRule;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * 分析错误检查规则 (priority=4, blocking=true)
 * <p>
 * 检查仪器报警/分析错误标记。resultText 含 ERROR/ALARM 等关键字视为异常。
 */
@Component
public class AnalyticalErrorCheck implements AutoAuditRule {

    private static final List<String> ERROR_KEYWORDS = List.of(
            "ERROR", "ALARM", "FAULT", "OVERFLOW", "----", "****", "NO DATA"
    );

    @Override
    public String ruleName() {
        return "AnalyticalErrorCheck";
    }

    @Override
    public int priority() {
        return 4;
    }

    @Override
    public boolean isBlocking() {
        return true;
    }

    @Override
    public AuditResult audit(ReportVO report) {
        List<String> errorItems = new ArrayList<>();
        for (ResultVO r : report.getResults()) {
            String text = r.getResultText();
            String value = r.getResultValue();
            if (matchError(text) || matchError(value)) {
                errorItems.add(r.getTestItemCode() + "=" + value + "(" + text + ")");
            }
        }
        if (errorItems.isEmpty()) {
            return AuditResult.pass(ruleName());
        }
        return AuditResult.fail(ruleName(),
                "仪器报警/分析错误: " + String.join("; ", errorItems));
    }

    private boolean matchError(String s) {
        if (s == null || s.isBlank()) {
            return false;
        }
        String upper = s.toUpperCase();
        return ERROR_KEYWORDS.stream().anyMatch(upper::contains);
    }
}
