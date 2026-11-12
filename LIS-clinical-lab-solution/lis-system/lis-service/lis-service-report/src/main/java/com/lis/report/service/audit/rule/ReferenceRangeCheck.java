package com.lis.report.service.audit.rule;

import com.lis.api.vo.ReportVO;
import com.lis.api.vo.ResultVO;
import com.lis.report.service.audit.AuditResult;
import com.lis.report.service.audit.AutoAuditRule;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * 参考范围检查规则 (priority=1)
 * <p>
 * 检查每项结果是否在参考范围内；超出范围标记异常但不阻断 (建议人工复核)。
 */
@Component
public class ReferenceRangeCheck implements AutoAuditRule {

    @Override
    public String ruleName() {
        return "ReferenceRangeCheck";
    }

    @Override
    public int priority() {
        return 1;
    }

    @Override
    public boolean isBlocking() {
        return false;
    }

    @Override
    public AuditResult audit(ReportVO report) {
        List<String> abnormalItems = new ArrayList<>();
        for (ResultVO r : report.getResults()) {
            if (r.getRefRange() == null || r.getRefRange().isBlank()) {
                continue;
            }
            if (r.getResultValue() == null || r.getResultValue().isBlank()) {
                continue;
            }
            if (!inReferenceRange(r.getResultValue(), r.getRefRange())) {
                abnormalItems.add(r.getTestItemCode() + "=" + r.getResultValue() + "(" + r.getRefRange() + ")");
            }
        }
        if (abnormalItems.isEmpty()) {
            return AuditResult.pass(ruleName());
        }
        return AuditResult.fail(ruleName(), "结果超出参考范围: " + String.join("; ", abnormalItems));
    }

    /**
     * 简化判定: 支持形如 "3.5-5.5"、"<10"、">1.0" 的参考范围
     */
    private boolean inReferenceRange(String value, String refRange) {
        try {
            double v = Double.parseDouble(value.trim());
            String r = refRange.trim();
            if (r.contains("-")) {
                String[] parts = r.split("-");
                if (parts.length == 2) {
                    double low = Double.parseDouble(parts[0].trim());
                    double high = Double.parseDouble(parts[1].trim());
                    return v >= low && v <= high;
                }
            } else if (r.startsWith("<") || r.startsWith("≤")) {
                double high = Double.parseDouble(r.substring(1).trim());
                return v <= high;
            } else if (r.startsWith(">") || r.startsWith("≥")) {
                double low = Double.parseDouble(r.substring(1).trim());
                return v >= low;
            }
        } catch (NumberFormatException e) {
            // 非数字结果 (如阴性/阳性) 默认通过
            return true;
        }
        return true;
    }
}
