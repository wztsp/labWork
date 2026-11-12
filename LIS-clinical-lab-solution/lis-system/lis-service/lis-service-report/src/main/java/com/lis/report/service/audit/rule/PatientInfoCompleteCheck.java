package com.lis.report.service.audit.rule;

import com.lis.api.vo.ReportVO;
import com.lis.report.service.audit.AuditResult;
import com.lis.report.service.audit.AutoAuditRule;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * 患者信息完整性检查规则 (priority=5, blocking=true)
 * <p>
 * 检查报告上患者基础信息是否完整 (姓名、ID、报告类型)。
 */
@Component
public class PatientInfoCompleteCheck implements AutoAuditRule {

    @Override
    public String ruleName() {
        return "PatientInfoCompleteCheck";
    }

    @Override
    public int priority() {
        return 5;
    }

    @Override
    public boolean isBlocking() {
        return true;
    }

    @Override
    public AuditResult audit(ReportVO report) {
        List<String> missing = new ArrayList<>();
        if (report.getPatientId() == null) {
            missing.add("patientId");
        }
        if (report.getPatientName() == null || report.getPatientName().isBlank()) {
            missing.add("patientName");
        }
        if (report.getReportType() == null) {
            missing.add("reportType");
        }
        if (report.getSampleId() == null) {
            missing.add("sampleId");
        }
        if (missing.isEmpty()) {
            return AuditResult.pass(ruleName());
        }
        return AuditResult.fail(ruleName(), "患者信息不完整，缺失字段: " + String.join(", ", missing));
    }
}
