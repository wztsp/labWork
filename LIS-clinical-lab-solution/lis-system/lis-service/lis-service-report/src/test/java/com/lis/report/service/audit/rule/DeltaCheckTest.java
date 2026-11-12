package com.lis.report.service.audit.rule;

import com.lis.api.vo.ReportVO;
import com.lis.api.vo.ResultVO;
import com.lis.report.entity.Result;
import com.lis.report.mapper.ResultMapper;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * DeltaCheck 单元测试
 * <p>
 * 注意：DeltaCheck 的 deltaThreshold 由 @Value 注入（默认 20.0）。
 * 单元测试直接 new 对象时该字段为 0，需通过反射设为生产默认值 20.0。
 */
class DeltaCheckTest {

    private final ResultMapper resultMapper = mock(ResultMapper.class);

    private DeltaCheck buildEngine() throws Exception {
        DeltaCheck engine = new DeltaCheck(resultMapper);
        Field f = DeltaCheck.class.getDeclaredField("deltaThreshold");
        f.setAccessible(true);
        f.set(engine, 20.0);
        return engine;
    }

    private ReportVO report(String value) {
        ReportVO r = new ReportVO();
        r.setPatientId(1L);
        r.setSampleId(1L);
        ResultVO res = new ResultVO();
        res.setTestItemId(10L);
        res.setTestItemCode("GLU");
        res.setResultValue(value);
        r.setResults(List.of(res));
        return r;
    }

    private Result history(String v) {
        Result h = new Result();
        h.setResultValue(v);
        return h;
    }

    @Test
    void deltaExceedThreshold_shouldFail() throws Exception {
        when(resultMapper.selectLastPublishedByPatientAndItem(1L, 10L, 1L)).thenReturn(history("50"));
        // |100-50|/50 = 100% > 20%
        assertFalse(buildEngine().audit(report("100")).isPassed());
    }

    @Test
    void deltaWithinThreshold_shouldPass() throws Exception {
        when(resultMapper.selectLastPublishedByPatientAndItem(1L, 10L, 1L)).thenReturn(history("50"));
        // |55-50|/50 = 10% < 20%
        assertTrue(buildEngine().audit(report("55")).isPassed());
    }

    @Test
    void deltaAtBoundary_shouldPass() throws Exception {
        when(resultMapper.selectLastPublishedByPatientAndItem(1L, 10L, 1L)).thenReturn(history("50"));
        // |60-50|/50 = 20% 不大于 20% -> 通过
        assertTrue(buildEngine().audit(report("60")).isPassed());
    }

    @Test
    void noHistory_shouldPass() throws Exception {
        when(resultMapper.selectLastPublishedByPatientAndItem(1L, 10L, 1L)).thenReturn(null);
        assertTrue(buildEngine().audit(report("100")).isPassed());
    }

    @Test
    void historyZero_shouldSkip() throws Exception {
        when(resultMapper.selectLastPublishedByPatientAndItem(1L, 10L, 1L)).thenReturn(history("0"));
        assertTrue(buildEngine().audit(report("100")).isPassed());
    }

    @Test
    void missingPatientId_shouldPass() throws Exception {
        ReportVO r = report("100");
        r.setPatientId(null);
        assertTrue(buildEngine().audit(r).isPassed());
    }

    @Test
    void emptyResults_shouldPass() throws Exception {
        ReportVO r = new ReportVO();
        r.setPatientId(1L);
        r.setSampleId(1L);
        r.setResults(Collections.emptyList());
        assertTrue(buildEngine().audit(r).isPassed());
    }
}
