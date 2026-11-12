package com.lis.report.service.audit.rule;

import com.lis.api.vo.ReportVO;
import com.lis.api.vo.ResultVO;
import com.lis.report.service.audit.AuditResult;
import com.lis.report.service.audit.AutoAuditRule;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * 危急值检查规则 (priority=2, blocking=true)
 * <p>
 * 出现危急值时阻断自动审核，必须人工干预确认。
 */
@Component
public class CriticalValueCheck implements AutoAuditRule {

    @Override
    public String ruleName() {
        return "CriticalValueCheck";
    }

    @Override
    public int priority() {
        return 2;
    }

    @Override
    public boolean isBlocking() {
        return true;
    }

    @Override
    public AuditResult audit(ReportVO report) {
        List<String> criticalItems = new ArrayList<>();
        for (ResultVO r : report.getResults()) {
            if (r.getIsCritical() != null && r.getIsCritical() == 1) {
                criticalItems.add(r.getTestItemCode() + "=" + r.getResultValue());
            }
        }
        if (criticalItems.isEmpty()) {
            return AuditResult.pass(ruleName());
        }
        return AuditResult.fail(ruleName(),
                "存在危急值，必须人工确认: " + String.join("; ", criticalItems));
    }
}
