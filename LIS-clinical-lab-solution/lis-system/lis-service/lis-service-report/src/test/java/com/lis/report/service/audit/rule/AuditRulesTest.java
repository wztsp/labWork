package com.lis.report.service.audit.rule;

import com.lis.api.vo.ReportVO;
import com.lis.api.vo.ResultVO;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 自动审核 4 条纯逻辑规则测试
 * (ReferenceRangeCheck / CriticalValueCheck / PatientInfoCompleteCheck / AnalyticalErrorCheck)
 */
class AuditRulesTest {

    private static ResultVO res(String value, String refRange, Integer critical, String text) {
        ResultVO r = new ResultVO();
        r.setTestItemCode("GLU");
        r.setResultValue(value);
        r.setRefRange(refRange);
        r.setIsCritical(critical);
        r.setResultText(text);
        return r;
    }

    // ---------------- ReferenceRangeCheck ----------------
    @Test
    void referenceRange_withinRange_pass() {
        ReportVO r = new ReportVO();
        r.setResults(List.of(res("5.0", "3.5-5.5", null, null)));
        assertTrue(new ReferenceRangeCheck().audit(r).isPassed());
    }

    @Test
    void referenceRange_outOfRange_fail() {
        ReportVO r = new ReportVO();
        r.setResults(List.of(res("6.0", "3.5-5.5", null, null)));
        assertFalse(new ReferenceRangeCheck().audit(r).isPassed());
    }

    @Test
    void referenceRange_lessThan_passAndFail() {
        ReportVO pass = new ReportVO();
        pass.setResults(List.of(res("5", "<10", null, null)));
        assertTrue(new ReferenceRangeCheck().audit(pass).isPassed());

        ReportVO fail = new ReportVO();
        fail.setResults(List.of(res("11", "<10", null, null)));
        assertFalse(new ReferenceRangeCheck().audit(fail).isPassed());
    }

    @Test
    void referenceRange_greaterThan_passAndFail() {
        ReportVO pass = new ReportVO();
        pass.setResults(List.of(res("2", ">1.0", null, null)));
        assertTrue(new ReferenceRangeCheck().audit(pass).isPassed());

        ReportVO fail = new ReportVO();
        fail.setResults(List.of(res("0.5", ">1.0", null, null)));
        assertFalse(new ReferenceRangeCheck().audit(fail).isPassed());
    }

    @Test
    void referenceRange_nonNumericValue_pass() {
        // 非数字结果默认通过
        ReportVO r = new ReportVO();
        r.setResults(List.of(res("阴性", "阴性-阳性", null, null)));
        assertTrue(new ReferenceRangeCheck().audit(r).isPassed());
    }

    @Test
    void referenceRange_blankRefRange_skip() {
        ReportVO r = new ReportVO();
        r.setResults(List.of(res("5.0", "", null, null)));
        assertTrue(new ReferenceRangeCheck().audit(r).isPassed());
    }

    // ---------------- CriticalValueCheck ----------------
    @Test
    void criticalValue_present_shouldFail() {
        ReportVO r = new ReportVO();
        r.setResults(List.of(res("1.2", "3.5-5.5", 1, null)));
        assertFalse(new CriticalValueCheck().audit(r).isPassed());
        assertTrue(new CriticalValueCheck().isBlocking());
    }

    @Test
    void criticalValue_absent_shouldPass() {
        ReportVO r = new ReportVO();
        r.setResults(List.of(res("5.0", "3.5-5.5", 0, null)));
        assertTrue(new CriticalValueCheck().audit(r).isPassed());
    }

    // ---------------- PatientInfoCompleteCheck ----------------
    @Test
    void patientInfo_complete_shouldPass() {
        ReportVO r = new ReportVO();
        r.setSampleId(1L);
        r.setPatientId(1L);
        r.setPatientName("张三");
        r.setReportType(1);
        assertTrue(new PatientInfoCompleteCheck().audit(r).isPassed());
    }

    @Test
    void patientInfo_missingName_shouldFail() {
        ReportVO r = new ReportVO();
        r.setSampleId(1L);
        r.setPatientId(1L);
        r.setPatientName("");
        r.setReportType(1);
        assertFalse(new PatientInfoCompleteCheck().audit(r).isPassed());
    }

    @Test
    void patientInfo_missingReportType_shouldFail() {
        ReportVO r = new ReportVO();
        r.setSampleId(1L);
        r.setPatientId(1L);
        r.setPatientName("张三");
        assertFalse(new PatientInfoCompleteCheck().audit(r).isPassed());
    }

    // ---------------- AnalyticalErrorCheck ----------------
    @Test
    void analyticalError_textContainsError_shouldFail() {
        ReportVO r = new ReportVO();
        r.setResults(List.of(res("5.5", "3.5-6.1", null, "ERROR sensor fault")));
        assertFalse(new AnalyticalErrorCheck().audit(r).isPassed());
    }

    @Test
    void analyticalError_valueContainsAlarm_shouldFail() {
        ReportVO r = new ReportVO();
        r.setResults(List.of(res("ALARM", "3.5-6.1", null, null)));
        assertFalse(new AnalyticalErrorCheck().audit(r).isPassed());
    }

    @Test
    void analyticalError_normal_shouldPass() {
        ReportVO r = new ReportVO();
        r.setResults(List.of(res("5.5", "3.5-6.1", null, null)));
        assertTrue(new AnalyticalErrorCheck().audit(r).isPassed());
    }
}
