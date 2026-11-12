package com.lis.micro.service;

import com.lis.micro.entity.AntibioticBreakpoint;
import com.lis.micro.interpret.SirCategory;
import com.lis.micro.interpret.SusceptibilityInterpreter;
import com.lis.micro.interpret.dto.InterpretRequest;
import com.lis.micro.interpret.dto.InterpretResult;
import com.lis.micro.service.impl.SusceptibilityInterpretServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * {@link SusceptibilityInterpretServiceImpl} 接入层单元测试。
 *
 * <p>不加载 Spring。通过 2 参构造函数注入 mock 判读器, 使被测范围收敛到
 * Service 自身的三项职责: <b>批量去重取折点(避免 N+1)</b>、<b>人工值优先</b>、
 * <b>取数异常降级</b>。判读算法本身由
 * {@code ClsiSusceptibilityInterpreterTest} 覆盖。</p>
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class SusceptibilityInterpretServiceImplTest {

    @Mock
    private AntibioticBreakpointService breakpointService;

    @Mock
    private SusceptibilityInterpreter interpreter;

    @Captor
    private ArgumentCaptor<Map<String, List<AntibioticBreakpoint>>> candidateMapCaptor;

    private SusceptibilityInterpretServiceImpl service;

    private static final Long BACTERIA_ID = 1L;
    private static final Long ANTIBIOTIC_ID = 10L;

    @BeforeEach
    void setUp() {
        service = new SusceptibilityInterpretServiceImpl(breakpointService, interpreter);
    }

    // ==================== 夹具 ====================

    private static InterpretRequest req(Long bacteriaId, Long antibioticId, String rawValue) {
        InterpretRequest r = new InterpretRequest();
        r.setBacteriaId(bacteriaId);
        r.setAntibioticId(antibioticId);
        r.setMethod(InterpretRequest.METHOD_MIC);
        r.setRawValue(rawValue);
        return r;
    }

    private static AntibioticBreakpoint bp(Long id) {
        AntibioticBreakpoint b = new AntibioticBreakpoint();
        b.setId(id);
        b.setBacteriaId(BACTERIA_ID);
        b.setAntibioticId(ANTIBIOTIC_ID);
        b.setMethod("微量肉汤法");
        b.setStandard("CLSI");
        b.setStandardVersion("M100-2023");
        b.setSBreakpoint("≤2");
        b.setRBreakpoint("≥8");
        b.setStatus(1);
        return b;
    }

    private static InterpretResult engineResult(SirCategory sir, InterpretRequest req) {
        return InterpretResult.of(sir, req, new BigDecimal("1"), "引擎判读依据");
    }

    private static List<InterpretResult> engineResults(SirCategory sir, List<InterpretRequest> reqs) {
        List<InterpretResult> list = new ArrayList<>(reqs.size());
        for (InterpretRequest r : reqs) {
            list.add(engineResult(sir, r));
        }
        return list;
    }

    // ==================== 批量去重取折点 (避免 N+1) ====================

    @Test
    @DisplayName("interpretBatch: 3 条同 (菌,药) 组合只查 1 次折点, 避免 N+1")
    void interpretBatch_sameBacteriaAntibioticPair_fetchesBreakpointsOnce() {
        List<InterpretRequest> reqs = Arrays.asList(
                req(BACTERIA_ID, ANTIBIOTIC_ID, "1"),
                req(BACTERIA_ID, ANTIBIOTIC_ID, "4"),
                req(BACTERIA_ID, ANTIBIOTIC_ID, "16"));
        when(breakpointService.listByAntibioticAndBacteria(ANTIBIOTIC_ID, BACTERIA_ID))
                .thenReturn(Collections.singletonList(bp(100L)));
        when(interpreter.interpretBatch(anyList(), any())).thenReturn(engineResults(SirCategory.S, reqs));

        List<InterpretResult> results = service.interpretBatch(reqs);

        assertEquals(3, results.size());
        verify(breakpointService, times(1))
                .listByAntibioticAndBacteria(ANTIBIOTIC_ID, BACTERIA_ID);
        verify(breakpointService, times(1)).listByAntibioticAndBacteria(anyLong(), anyLong());
    }

    @Test
    @DisplayName("interpretBatch: 2 个不同 (菌,药) 组合各查 1 次, 共 2 次")
    void interpretBatch_distinctPairs_fetchesOncePerPair() {
        List<InterpretRequest> reqs = Arrays.asList(
                req(BACTERIA_ID, ANTIBIOTIC_ID, "1"),
                req(BACTERIA_ID, 20L, "1"),
                req(BACTERIA_ID, ANTIBIOTIC_ID, "4"));
        when(breakpointService.listByAntibioticAndBacteria(anyLong(), anyLong()))
                .thenReturn(Collections.singletonList(bp(100L)));
        when(interpreter.interpretBatch(anyList(), any())).thenReturn(engineResults(SirCategory.S, reqs));

        service.interpretBatch(reqs);

        verify(breakpointService, times(2)).listByAntibioticAndBacteria(anyLong(), anyLong());
        verify(breakpointService).listByAntibioticAndBacteria(ANTIBIOTIC_ID, BACTERIA_ID);
        verify(breakpointService).listByAntibioticAndBacteria(20L, BACTERIA_ID);
    }

    @Test
    @DisplayName("interpretBatch: 传给判读器的 candidateMap 以 candidateKey 为键且已去重")
    void interpretBatch_candidateMapKeyedByCandidateKey_andDeduplicated() {
        List<InterpretRequest> reqs = Arrays.asList(
                req(BACTERIA_ID, ANTIBIOTIC_ID, "1"),
                req(BACTERIA_ID, ANTIBIOTIC_ID, "4"),
                req(BACTERIA_ID, 20L, "2"));
        when(breakpointService.listByAntibioticAndBacteria(anyLong(), anyLong()))
                .thenReturn(Collections.singletonList(bp(100L)));
        when(interpreter.interpretBatch(anyList(), any())).thenReturn(engineResults(SirCategory.S, reqs));

        service.interpretBatch(reqs);

        verify(interpreter).interpretBatch(anyList(), candidateMapCaptor.capture());
        Map<String, List<AntibioticBreakpoint>> captured = candidateMapCaptor.getValue();
        assertAll(
                () -> assertEquals(2, captured.size(), "3 条请求 2 个组合, map 应只有 2 个 key"),
                () -> assertTrue(captured.containsKey(
                        SusceptibilityInterpreter.candidateKey(BACTERIA_ID, ANTIBIOTIC_ID))),
                () -> assertTrue(captured.containsKey(
                        SusceptibilityInterpreter.candidateKey(BACTERIA_ID, 20L)))
        );
    }

    @Test
    @DisplayName("interpretBatch: 入参为空 / null → 返回空列表且不查库")
    void interpretBatch_emptyOrNullInput_returnsEmptyListWithoutQuery() {
        assertTrue(service.interpretBatch(null).isEmpty());
        assertTrue(service.interpretBatch(Collections.emptyList()).isEmpty());

        verifyNoInteractions(breakpointService);
        verifyNoInteractions(interpreter);
    }

    @Test
    @DisplayName("interpretBatch: bacteriaId / antibioticId 为空时跳过查库, 直接给空折点")
    void interpretBatch_nullIds_skipsBreakpointQuery() {
        List<InterpretRequest> reqs = Collections.singletonList(req(null, ANTIBIOTIC_ID, "1"));
        when(interpreter.interpretBatch(anyList(), any())).thenReturn(engineResults(SirCategory.NI, reqs));

        service.interpretBatch(reqs);

        verify(breakpointService, never()).listByAntibioticAndBacteria(anyLong(), anyLong());
        verify(interpreter).interpretBatch(anyList(), candidateMapCaptor.capture());
        assertTrue(candidateMapCaptor.getValue()
                .get(SusceptibilityInterpreter.candidateKey(null, ANTIBIOTIC_ID)).isEmpty());
    }

    // ==================== 人工值优先 ====================

    @Test
    @DisplayName("interpret: 带人工结果时人工值优先, 引擎结果不覆盖")
    void interpret_manualResultPresent_manualWins() {
        InterpretRequest request = req(BACTERIA_ID, ANTIBIOTIC_ID, "1");
        request.setManualResult("R");
        when(breakpointService.listByAntibioticAndBacteria(ANTIBIOTIC_ID, BACTERIA_ID))
                .thenReturn(Collections.singletonList(bp(100L)));
        when(interpreter.interpret(any(), any())).thenReturn(engineResult(SirCategory.S, request));

        InterpretResult result = service.interpret(request);

        assertAll(
                () -> assertEquals(SirCategory.R, result.getSir(), "人工 R 必须覆盖引擎 S"),
                () -> assertFalse(result.isDegraded(), "人工判读不算降级"),
                () -> assertTrue(result.getBasis().startsWith("人工判读结果: R")),
                () -> assertTrue(result.getBasis().contains("引擎参考依据"), "应保留引擎依据供追溯"),
                () -> assertNotNull(result.getMessage())
        );
    }

    @Test
    @DisplayName("interpret: 引擎降级 (NI) 但带人工结果时, 仍以人工值为准且不标降级")
    void interpret_engineDegradedWithManualResult_manualWinsAndNotDegraded() {
        InterpretRequest request = req(BACTERIA_ID, ANTIBIOTIC_ID, "NG");
        request.setManualResult("S");
        when(interpreter.interpret(any(), any()))
                .thenReturn(InterpretResult.degraded(request, "原始值非数值: 'NG'"));

        InterpretResult result = service.interpret(request);

        assertEquals(SirCategory.S, result.getSir());
        assertFalse(result.isDegraded());
    }

    @Test
    @DisplayName("interpret: 人工结果为空白串时保留引擎结果")
    void interpret_blankManualResult_keepsEngineResult() {
        InterpretRequest request = req(BACTERIA_ID, ANTIBIOTIC_ID, "1");
        request.setManualResult("   ");
        when(interpreter.interpret(any(), any())).thenReturn(engineResult(SirCategory.S, request));

        InterpretResult result = service.interpret(request);

        assertEquals(SirCategory.S, result.getSir());
        assertEquals("引擎判读依据", result.getBasis());
    }

    @Test
    @DisplayName("interpret: 人工结果为非法代码时归一化为 NI, 仍不被引擎覆盖")
    void interpret_unknownManualCode_normalizedToNi() {
        InterpretRequest request = req(BACTERIA_ID, ANTIBIOTIC_ID, "1");
        request.setManualResult("XX");
        when(interpreter.interpret(any(), any())).thenReturn(engineResult(SirCategory.S, request));

        InterpretResult result = service.interpret(request);

        assertEquals(SirCategory.NI, result.getSir());
    }

    @Test
    @DisplayName("interpretBatch: 人工值优先逐条生效, 未带人工值的条目保留引擎结果")
    void interpretBatch_manualPriorityAppliedPerElement() {
        InterpretRequest withManual = req(BACTERIA_ID, ANTIBIOTIC_ID, "1");
        withManual.setManualResult("R");
        InterpretRequest withoutManual = req(BACTERIA_ID, ANTIBIOTIC_ID, "1");
        List<InterpretRequest> reqs = Arrays.asList(withManual, withoutManual);

        when(breakpointService.listByAntibioticAndBacteria(anyLong(), anyLong()))
                .thenReturn(Collections.singletonList(bp(100L)));
        when(interpreter.interpretBatch(anyList(), any())).thenReturn(engineResults(SirCategory.S, reqs));

        List<InterpretResult> results = service.interpretBatch(reqs);

        assertEquals(2, results.size());
        assertEquals(SirCategory.R, results.get(0).getSir(), "第 1 条应取人工值");
        assertEquals(SirCategory.S, results.get(1).getSir(), "第 2 条应保留引擎值");
    }

    // ==================== 降级与健壮性 ====================

    @Test
    @DisplayName("interpret: 入参为 null → 降级返回 NI, 不查库不抛异常")
    void interpret_nullRequest_returnsDegraded() {
        InterpretResult result = service.interpret(null);

        assertEquals(SirCategory.NI, result.getSir());
        assertTrue(result.isDegraded());
        verifyNoInteractions(breakpointService);
        verifyNoInteractions(interpreter);
    }

    @Test
    @DisplayName("interpret: 折点查询抛异常时吞掉异常, 以空折点集合交给判读器降级")
    void interpret_breakpointQueryThrows_fallsBackToEmptyCandidates() {
        InterpretRequest request = req(BACTERIA_ID, ANTIBIOTIC_ID, "1");
        when(breakpointService.listByAntibioticAndBacteria(anyLong(), anyLong()))
                .thenThrow(new RuntimeException("数据库连接失败"));
        when(interpreter.interpret(any(), any()))
                .thenReturn(InterpretResult.degraded(request, "未配置CLSI折点"));

        InterpretResult result = service.interpret(request);

        assertEquals(SirCategory.NI, result.getSir());
        assertTrue(result.isDegraded());
        verify(interpreter).interpret(eq(request), eq(Collections.<AntibioticBreakpoint>emptyList()));
    }

    @Test
    @DisplayName("interpret: 折点服务返回 null 时归一化为空集合, 不 NPE")
    void interpret_breakpointServiceReturnsNull_normalizedToEmptyList() {
        InterpretRequest request = req(BACTERIA_ID, ANTIBIOTIC_ID, "1");
        when(breakpointService.listByAntibioticAndBacteria(anyLong(), anyLong())).thenReturn(null);
        when(interpreter.interpret(any(), any()))
                .thenReturn(InterpretResult.degraded(request, "未配置CLSI折点"));

        InterpretResult result = service.interpret(request);

        assertNotNull(result);
        verify(interpreter).interpret(eq(request), eq(Collections.<AntibioticBreakpoint>emptyList()));
    }

    @Test
    @DisplayName("构造函数: 传入 null 判读器时回退为默认 CLSI 判读器, 仍可正常判读")
    void constructor_nullInterpreter_fallsBackToDefaultClsiInterpreter() {
        SusceptibilityInterpretServiceImpl svc =
                new SusceptibilityInterpretServiceImpl(breakpointService, null);
        when(breakpointService.listByAntibioticAndBacteria(ANTIBIOTIC_ID, BACTERIA_ID))
                .thenReturn(Collections.singletonList(bp(100L)));

        InterpretResult result = svc.interpret(req(BACTERIA_ID, ANTIBIOTIC_ID, "1"));

        assertEquals(SirCategory.S, result.getSir());
        assertFalse(result.isDegraded());
    }
}
