package com.lis.report.service.audit;

import com.lis.api.vo.ReportVO;
import com.lis.api.vo.ResultVO;
import com.lis.report.entity.Result;
import com.lis.report.mapper.ResultMapper;
import com.lis.report.service.audit.rule.AnalyticalErrorCheck;
import com.lis.report.service.audit.rule.CriticalValueCheck;
import com.lis.report.service.audit.rule.DeltaCheck;
import com.lis.report.service.audit.rule.PatientInfoCompleteCheck;
import com.lis.report.service.audit.rule.ReferenceRangeCheck;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * AutoAuditEngine 单元测试：验证优先级排序与阻断语义
 */
class AutoAuditEngineTest {

    private final ResultMapper resultMapper = mock(ResultMapper.class);

    private AutoAuditEngine buildEngine() {
        // 按 priority 升序注入规则（引擎内部也会再排序，确保顺序正确）
        return new AutoAuditEngine(List.of(
                new ReferenceRangeCheck(),   // 1, non-blocking
                new CriticalValueCheck(),    // 2, blocking
                new DeltaCheck(resultMapper),// 3, non-blocking
                new AnalyticalErrorCheck(),  // 4, blocking
                new PatientInfoCompleteCheck() // 5, blocking
        ));
    }

    private ReportVO baseReport() {
        ReportVO r = new ReportVO();
        r.setId(1L);
        r.setSampleId(1L);
        r.setPatientId(1L);
        r.setPatientName("张三");
        r.setReportType(1);
        ResultVO res = new ResultVO();
        res.setTestItemId(10L);
        res.setTestItemCode("GLU");
        res.setResultValue("5.5");
        res.setRefRange("3.5-6.1");
        r.setResults(List.of(res));
        return r;
    }

    @Test
    void evaluate_nullReport_shouldFailPreCheck() {
        AuditResult result = buildEngine().evaluate(null);
        assertFalse(result.isPassed());
        assertEquals("PreCheck", result.getRuleName());
    }

    @Test
    void evaluate_allPass_shouldPass() {
        AuditResult result = buildEngine().evaluate(baseReport());
        assertTrue(result.isPassed());
    }

    @Test
    void evaluate_blockingCriticalValue_shouldFail() {
        ReportVO r = baseReport();
        r.getResults().get(0).setIsCritical(1);
        AuditResult result = buildEngine().evaluate(r);
        assertFalse(result.isPassed());
        assertEquals("CriticalValueCheck", result.getRuleName());
    }

    @Test
    void evaluate_nonBlockingOnly_shouldStillPass() {
        // 参考范围超出 (non-blocking) 不应阻断整批审核
        ReportVO r = baseReport();
        r.getResults().get(0).setResultValue("999");
        r.getResults().get(0).setRefRange("3.5-6.1");
        AuditResult result = buildEngine().evaluate(r);
        assertTrue(result.isPassed());
    }

    @Test
    void evaluate_priorityOrder_criticalBeforePatientInfo() {
        // 同时触发危急值(priority2)与患者信息缺失(priority5) -> 应返回优先级更高的 CriticalValueCheck
        ReportVO r = baseReport();
        r.getResults().get(0).setIsCritical(1);
        r.setPatientName(null); // 触发 PatientInfoCompleteCheck
        AuditResult result = buildEngine().evaluate(r);
        assertFalse(result.isPassed());
        assertEquals("CriticalValueCheck", result.getRuleName());
    }

    @Test
    void evaluate_deltaExceedThreshold_shouldFailNonBlocking() {
        // Delta 超限 -> 失败但不阻断；其余通过 -> 整批通过
        ReportVO r = baseReport();
        r.getResults().get(0).setResultValue("100");
        Result history = mock(Result.class);
        when(history.getResultValue()).thenReturn("50"); // |100-50|/50 = 100% > 20%
        when(resultMapper.selectLastPublishedByPatientAndItem(1L, 10L, 1L)).thenReturn(history);
        AuditResult result = buildEngine().evaluate(r);
        assertTrue(result.isPassed());
    }
}
