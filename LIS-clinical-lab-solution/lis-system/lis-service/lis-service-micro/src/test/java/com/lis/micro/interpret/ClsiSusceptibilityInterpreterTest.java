package com.lis.micro.interpret;

import com.lis.micro.entity.AntibioticBreakpoint;
import com.lis.micro.interpret.dto.InterpretRequest;
import com.lis.micro.interpret.dto.InterpretResult;
import com.lis.micro.interpret.impl.ClsiSusceptibilityInterpreter;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link ClsiSusceptibilityInterpreter} CLSI 判读算法核心单元测试。
 *
 * <p><b>零 Spring / 零 mock</b>: 被测类为纯 POJO, 直接 {@code new} 即可,
 * 候选折点手工构造。覆盖 MIC 三分支 + 边界等值、K-B 反向判定、
 * 版本择新、方法学归一化, 以及 5 类降级场景。</p>
 *
 * <p>约定: 判读器<b>只返回不抛出</b>, 因此降级场景一律断言返回值,
 * 不使用 {@code assertThrows}。</p>
 */
class ClsiSusceptibilityInterpreterTest {

    private final ClsiSusceptibilityInterpreter interpreter = new ClsiSusceptibilityInterpreter();

    private static final Long BACTERIA_ID = 1L;
    private static final Long ANTIBIOTIC_ID = 10L;

    // ==================== 夹具工厂 ====================

    /** MIC 折点夹具 */
    private static AntibioticBreakpoint micBp(Long id, String version,
                                              String sBreakpoint, String rBreakpoint, String iBreakpoint) {
        AntibioticBreakpoint bp = new AntibioticBreakpoint();
        bp.setId(id);
        bp.setBacteriaId(BACTERIA_ID);
        bp.setAntibioticId(ANTIBIOTIC_ID);
        bp.setMethod("微量肉汤法");
        bp.setStandard("CLSI");
        bp.setStandardVersion(version);
        bp.setSBreakpoint(sBreakpoint);
        bp.setRBreakpoint(rBreakpoint);
        bp.setIBreakpoint(iBreakpoint);
        bp.setStatus(1);
        return bp;
    }

    /** 标准 MIC 折点: S≤2 / R≥8 */
    private static AntibioticBreakpoint defaultMicBp() {
        return micBp(100L, "M100-2023", "≤2", "≥8", null);
    }

    /** K-B 折点夹具: 抑菌圈直径, 方向与 MIC 相反 */
    private static AntibioticBreakpoint kbBp(Long id, String sDd, String rDd) {
        AntibioticBreakpoint bp = new AntibioticBreakpoint();
        bp.setId(id);
        bp.setBacteriaId(BACTERIA_ID);
        bp.setAntibioticId(ANTIBIOTIC_ID);
        bp.setMethod("K-B纸片法");
        bp.setStandard("CLSI");
        bp.setStandardVersion("M100-2023");
        bp.setSDd(sDd);
        bp.setRDd(rDd);
        bp.setStatus(1);
        return bp;
    }

    /** 默认 K-B 折点: S≥18mm / R≤14mm, 中介区 15~17mm */
    private static AntibioticBreakpoint defaultKbBp() {
        return kbBp(200L, "≥18", "≤14");
    }

    private static InterpretRequest req(String method, String rawValue) {
        InterpretRequest r = new InterpretRequest();
        r.setBacteriaId(BACTERIA_ID);
        r.setAntibioticId(ANTIBIOTIC_ID);
        r.setMethod(method);
        r.setRawValue(rawValue);
        return r;
    }

    private static InterpretRequest micReq(String rawValue) {
        return req(InterpretRequest.METHOD_MIC, rawValue);
    }

    private static InterpretRequest kbReq(String rawValue) {
        return req(InterpretRequest.METHOD_KB, rawValue);
    }

    // ==================== MIC 判定 ====================

    @Nested
    @DisplayName("MIC 法: 数值越小越敏感")
    class MicInterpretation {

        @Test
        @DisplayName("interpretMic: 值远小于 S 阈值 → S")
        void interpretMic_valueBelowSBreakpoint_returnsS() {
            InterpretResult r = interpreter.interpret(micReq("0.5"),
                    Collections.singletonList(defaultMicBp()));

            assertAll(
                    () -> assertEquals(SirCategory.S, r.getSir()),
                    () -> assertFalse(r.isDegraded()),
                    () -> assertEquals(0, r.getNumericValue().compareTo(new BigDecimal("0.5"))),
                    () -> assertEquals(100L, r.getMatchedBreakpointId()),
                    () -> assertNotNull(r.getBasis())
            );
        }

        @Test
        @DisplayName("interpretMic: 值恰等于 S 阈值 (边界闭区间) → S")
        void interpretMic_valueEqualsSBreakpoint_returnsS() {
            InterpretResult r = interpreter.interpret(micReq("2"),
                    Collections.singletonList(defaultMicBp()));

            assertEquals(SirCategory.S, r.getSir(), "S 阈值为闭区间, 恰等应判 S");
            assertFalse(r.isDegraded());
        }

        @Test
        @DisplayName("interpretMic: 值恰等于 R 阈值 (边界闭区间) → R")
        void interpretMic_valueEqualsRBreakpoint_returnsR() {
            InterpretResult r = interpreter.interpret(micReq("8"),
                    Collections.singletonList(defaultMicBp()));

            assertEquals(SirCategory.R, r.getSir(), "R 阈值为闭区间, 恰等应判 R");
            assertFalse(r.isDegraded());
        }

        @Test
        @DisplayName("interpretMic: 值高于 R 阈值 → R")
        void interpretMic_valueAboveRBreakpoint_returnsR() {
            InterpretResult r = interpreter.interpret(micReq("32"),
                    Collections.singletonList(defaultMicBp()));

            assertEquals(SirCategory.R, r.getSir());
        }

        @Test
        @DisplayName("interpretMic: 值落 S/R 之间且未配置中介折点 → 推定 I")
        void interpretMic_valueBetweenThresholds_returnsIntermediate() {
            InterpretResult r = interpreter.interpret(micReq("4"),
                    Collections.singletonList(defaultMicBp()));

            assertAll(
                    () -> assertEquals(SirCategory.I, r.getSir()),
                    () -> assertFalse(r.isDegraded()),
                    () -> assertTrue(r.getBasis().contains("推定中介"))
            );
        }

        @Test
        @DisplayName("interpretMic: 值命中显式配置的中介折点 → I 且 basis 标注命中")
        void interpretMic_valueHitsExplicitIBreakpoint_returnsIntermediateWithBasis() {
            AntibioticBreakpoint bp = micBp(101L, "M100-2023", "≤2", "≥8", "4");

            InterpretResult r = interpreter.interpret(micReq("4"), Collections.singletonList(bp));

            assertEquals(SirCategory.I, r.getSir());
            assertTrue(r.getBasis().contains("命中中介折点"), "应使用显式中介折点作为依据");
        }

        @Test
        @DisplayName("interpretMic: 原始值带 ≤ 前缀 (如 ≤0.5) 按数值判 S")
        void interpretMic_rawValueWithLePrefix_returnsS() {
            InterpretResult r = interpreter.interpret(micReq("≤0.5"),
                    Collections.singletonList(defaultMicBp()));

            assertEquals(SirCategory.S, r.getSir());
            assertEquals(0, r.getNumericValue().compareTo(new BigDecimal("0.5")));
            assertEquals("≤0.5", r.getRawValue(), "原始值应原样回传");
        }

        @Test
        @DisplayName("interpretMic: 边界仲裁 — 原始值 ≥8 恰达 R 阈值 → R")
        void interpretMic_rawValueGePrefixEqualsRBreakpoint_arbitratedToR() {
            InterpretResult r = interpreter.interpret(micReq("≥8"),
                    Collections.singletonList(defaultMicBp()));

            assertEquals(SirCategory.R, r.getSir());
            assertTrue(r.getBasis().contains("不低于耐药阈值"), "应走下界型边界仲裁分支");
        }

        @Test
        @DisplayName("interpretMic: 边界仲裁 — 原始值 >64 远超 R 阈值 → R")
        void interpretMic_rawValueGtPrefixAboveRBreakpoint_arbitratedToR() {
            InterpretResult r = interpreter.interpret(micReq(">64"),
                    Collections.singletonList(defaultMicBp()));

            assertEquals(SirCategory.R, r.getSir());
        }

        @Test
        @DisplayName("interpretMic: method 为空时按 MIC 默认处理")
        void interpretMic_nullMethod_defaultsToMic() {
            InterpretRequest r = micReq("1");
            r.setMethod(null);

            InterpretResult result = interpreter.interpret(r, Collections.singletonList(defaultMicBp()));

            assertEquals(SirCategory.S, result.getSir());
            assertFalse(result.isDegraded());
        }
    }

    // ==================== K-B 判定 (方向相反) ====================

    @Nested
    @DisplayName("K-B 纸片扩散法: 抑菌圈越大越敏感 (方向与 MIC 相反)")
    class DiskDiffusionInterpretation {

        @Test
        @DisplayName("interpretKb: 抑菌圈远大于 S 阈值 → S (若误用 MIC 方向会判成 R)")
        void interpretKb_valueAboveSDd_returnsS() {
            InterpretResult r = interpreter.interpret(kbReq("25"),
                    Collections.singletonList(defaultKbBp()));

            assertAll(
                    () -> assertEquals(SirCategory.S, r.getSir(),
                            "K-B 法抑菌圈 25mm ≥ 18mm 必须判 S; 若判 R 说明方向写反"),
                    () -> assertFalse(r.isDegraded()),
                    () -> assertEquals(200L, r.getMatchedBreakpointId()),
                    () -> assertTrue(r.getBasis().contains("K-B法"))
            );
        }

        @Test
        @DisplayName("interpretKb: 抑菌圈恰等 S 阈值 18mm (闭区间) → S")
        void interpretKb_valueEqualsSDd_returnsS() {
            InterpretResult r = interpreter.interpret(kbReq("18"),
                    Collections.singletonList(defaultKbBp()));

            assertEquals(SirCategory.S, r.getSir());
        }

        @Test
        @DisplayName("interpretKb: 抑菌圈恰等 R 阈值 14mm (闭区间) → R")
        void interpretKb_valueEqualsRDd_returnsR() {
            InterpretResult r = interpreter.interpret(kbReq("14"),
                    Collections.singletonList(defaultKbBp()));

            assertEquals(SirCategory.R, r.getSir());
        }

        @Test
        @DisplayName("interpretKb: 抑菌圈小于 R 阈值 → R (若误用 MIC 方向会判成 S)")
        void interpretKb_valueBelowRDd_returnsR() {
            InterpretResult r = interpreter.interpret(kbReq("6"),
                    Collections.singletonList(defaultKbBp()));

            assertEquals(SirCategory.R, r.getSir(),
                    "K-B 法抑菌圈 6mm ≤ 14mm 必须判 R; 若判 S 说明方向写反");
        }

        @Test
        @DisplayName("interpretKb: 抑菌圈落 15~17mm 中介区 → I")
        void interpretKb_valueBetweenRDdAndSDd_returnsIntermediate() {
            InterpretResult r = interpreter.interpret(kbReq("16"),
                    Collections.singletonList(defaultKbBp()));

            assertEquals(SirCategory.I, r.getSir());
            assertFalse(r.isDegraded());
        }

        @Test
        @DisplayName("interpretKb: 中文方法学 '纸片扩散法' 归一化后仍走 K-B 分支")
        void interpretKb_chineseMethodName_normalizedToKb() {
            InterpretResult r = interpreter.interpret(req("纸片扩散法", "25"),
                    Collections.singletonList(defaultKbBp()));

            assertEquals(SirCategory.S, r.getSir());
            assertTrue(r.getBasis().contains("K-B法"), "中文方法学应归一化到 K-B");
        }

        @Test
        @DisplayName("normalizeMethod: 中英文方法学统一映射到 MIC / K-B")
        void normalizeMethod_variousInputs_mappedToCanonicalForm() {
            assertAll(
                    () -> assertEquals("K-B", ClsiSusceptibilityInterpreter.normalizeMethod("K-B纸片法")),
                    () -> assertEquals("K-B", ClsiSusceptibilityInterpreter.normalizeMethod("纸片扩散法")),
                    () -> assertEquals("K-B", ClsiSusceptibilityInterpreter.normalizeMethod("disk diffusion")),
                    () -> assertEquals("MIC", ClsiSusceptibilityInterpreter.normalizeMethod("微量肉汤法")),
                    () -> assertEquals("MIC", ClsiSusceptibilityInterpreter.normalizeMethod("E-test")),
                    () -> assertEquals("MIC", ClsiSusceptibilityInterpreter.normalizeMethod(null)),
                    () -> assertEquals("MIC", ClsiSusceptibilityInterpreter.normalizeMethod("   "))
            );
        }
    }

    // ==================== 折点选择 ====================

    @Nested
    @DisplayName("折点选择: 确定性可复现")
    class BreakpointSelection {

        @Test
        @DisplayName("selectBreakpoint: 多版本共存时取最新版本 M100-2023")
        void selectBreakpoint_multipleVersions_picksNewest() {
            // 2021 版更严格 (S≤1 / R≥4): 若误选 2021, 值 2 会被判成 I
            AntibioticBreakpoint old = micBp(1L, "M100-2021", "≤1", "≥4", null);
            AntibioticBreakpoint latest = micBp(2L, "M100-2023", "≤2", "≥8", null);

            InterpretResult r = interpreter.interpret(micReq("2"), Arrays.asList(old, latest));

            assertAll(
                    () -> assertEquals(2L, r.getMatchedBreakpointId(), "应命中 M100-2023"),
                    () -> assertEquals("M100-2023", r.getStandardVersion()),
                    () -> assertEquals(SirCategory.S, r.getSir(), "按 2023 版 S≤2, 值 2 应判 S")
            );
        }

        @Test
        @DisplayName("selectBreakpoint: 候选顺序颠倒不影响择新结果")
        void selectBreakpoint_orderIndependent_stillPicksNewest() {
            AntibioticBreakpoint latest = micBp(2L, "M100-2023", "≤2", "≥8", null);
            AntibioticBreakpoint old = micBp(1L, "M100-2021", "≤1", "≥4", null);

            InterpretResult reversed = interpreter.interpret(micReq("2"), Arrays.asList(latest, old));
            InterpretResult normal = interpreter.interpret(micReq("2"), Arrays.asList(old, latest));

            assertEquals(reversed.getMatchedBreakpointId(), normal.getMatchedBreakpointId());
            assertEquals(2L, reversed.getMatchedBreakpointId());
        }

        @Test
        @DisplayName("selectBreakpoint: 同版本多条时取 id 最小者, 保证可复现")
        void selectBreakpoint_sameVersion_picksSmallestId() {
            AntibioticBreakpoint big = micBp(9L, "M100-2023", "≤2", "≥8", null);
            AntibioticBreakpoint small = micBp(3L, "M100-2023", "≤2", "≥8", null);

            InterpretResult r = interpreter.interpret(micReq("1"), Arrays.asList(big, small));

            assertEquals(3L, r.getMatchedBreakpointId());
        }

        @Test
        @DisplayName("selectBreakpoint: status=0 的停用折点被过滤")
        void selectBreakpoint_disabledStatus_filteredOut() {
            AntibioticBreakpoint disabled = defaultMicBp();
            disabled.setStatus(0);

            InterpretResult r = interpreter.interpret(micReq("1"), Collections.singletonList(disabled));

            assertAll(
                    () -> assertEquals(SirCategory.NI, r.getSir()),
                    () -> assertTrue(r.isDegraded()),
                    () -> assertTrue(r.getBasis().contains("未配置"))
            );
        }

        @Test
        @DisplayName("selectBreakpoint: 标准不匹配 (EUCAST 折点 + CLSI 请求) 被过滤")
        void selectBreakpoint_standardMismatch_filteredOut() {
            AntibioticBreakpoint eucast = defaultMicBp();
            eucast.setStandard("EUCAST");

            InterpretResult r = interpreter.interpret(micReq("1"), Collections.singletonList(eucast));

            assertEquals(SirCategory.NI, r.getSir());
            assertTrue(r.isDegraded());
        }

        @Test
        @DisplayName("selectBreakpoint: 请求指定版本时精确过滤, 不回退到其他版本")
        void selectBreakpoint_explicitVersionNotPresent_degrades() {
            InterpretRequest request = micReq("1");
            request.setStandardVersion("M100-2019");

            InterpretResult r = interpreter.interpret(request, Collections.singletonList(defaultMicBp()));

            assertEquals(SirCategory.NI, r.getSir());
            assertTrue(r.isDegraded());
            assertTrue(r.getBasis().contains("M100-2019"));
        }

        @Test
        @DisplayName("selectBreakpoint: 方法学不匹配 (K-B 请求命中 MIC 折点) 被过滤")
        void selectBreakpoint_methodMismatch_filteredOut() {
            InterpretResult r = interpreter.interpret(kbReq("25"),
                    Collections.singletonList(defaultMicBp()));

            assertEquals(SirCategory.NI, r.getSir());
            assertTrue(r.isDegraded());
        }
    }

    // ==================== 5 类降级 ====================

    @Nested
    @DisplayName("降级处理: 一律返回 NI + degraded, 绝不抛异常")
    class Degradation {

        @Test
        @DisplayName("降级 1/5: 无匹配折点 → NI, basis 说明未配置")
        void degrade_noCandidates_returnsNiWithBasis() {
            InterpretResult r = interpreter.interpret(micReq("2"), Collections.emptyList());

            assertAll(
                    () -> assertEquals(SirCategory.NI, r.getSir()),
                    () -> assertTrue(r.isDegraded()),
                    () -> assertTrue(r.getBasis().contains("未配置CLSI折点")),
                    () -> assertTrue(r.getBasis().contains("bacteriaId=1")),
                    () -> assertTrue(r.getBasis().contains("antibioticId=10")),
                    () -> assertNotNull(r.getMessage())
            );
        }

        @Test
        @DisplayName("降级 1/5: candidates 为 null 同样降级而非 NPE")
        void degrade_nullCandidates_returnsNiWithoutThrowing() {
            InterpretResult r = assertDoesNotThrow(() -> interpreter.interpret(micReq("2"), null));

            assertEquals(SirCategory.NI, r.getSir());
            assertTrue(r.isDegraded());
        }

        @Test
        @DisplayName("降级 2/5: 折点表达式非法 (S=≤abc) → NI, basis 标注非法表达式")
        void degrade_illegalBreakpointExpression_returnsNi() {
            AntibioticBreakpoint bad = micBp(1L, "M100-2023", "≤abc", "≥8", null);

            InterpretResult r = interpreter.interpret(micReq("2"), Collections.singletonList(bad));

            assertAll(
                    () -> assertEquals(SirCategory.NI, r.getSir()),
                    () -> assertTrue(r.isDegraded()),
                    () -> assertTrue(r.getBasis().contains("折点表达式非法")),
                    () -> assertTrue(r.getBasis().contains("≤abc"))
            );
        }

        @Test
        @DisplayName("降级 2/5: R 折点表达式非法同样降级")
        void degrade_illegalRBreakpointExpression_returnsNi() {
            AntibioticBreakpoint bad = micBp(1L, "M100-2023", "≤2", "大于八", null);

            InterpretResult r = interpreter.interpret(micReq("4"), Collections.singletonList(bad));

            assertEquals(SirCategory.NI, r.getSir());
            assertTrue(r.getBasis().contains("折点表达式非法"));
        }

        @Test
        @DisplayName("降级 3/5: rawValue 非数值 'NG' → NI, basis 标注原始值非数值")
        void degrade_nonNumericRawValueNg_returnsNi() {
            InterpretResult r = interpreter.interpret(micReq("NG"),
                    Collections.singletonList(defaultMicBp()));

            assertAll(
                    () -> assertEquals(SirCategory.NI, r.getSir()),
                    () -> assertTrue(r.isDegraded()),
                    () -> assertTrue(r.getBasis().contains("原始值非数值")),
                    () -> assertNull(r.getNumericValue())
            );
        }

        @Test
        @DisplayName("降级 3/5: rawValue 为中文 '未检测' → NI")
        void degrade_nonNumericRawValueChinese_returnsNi() {
            InterpretResult r = interpreter.interpret(micReq("未检测"),
                    Collections.singletonList(defaultMicBp()));

            assertEquals(SirCategory.NI, r.getSir());
            assertTrue(r.getBasis().contains("原始值非数值"));
        }

        @Test
        @DisplayName("降级 4/5: 缺 S 阈值 → NI, basis 标注折点不完整")
        void degrade_missingSBreakpoint_returnsNi() {
            AntibioticBreakpoint incomplete = micBp(1L, "M100-2023", null, "≥8", null);

            InterpretResult r = interpreter.interpret(micReq("4"), Collections.singletonList(incomplete));

            assertEquals(SirCategory.NI, r.getSir());
            assertTrue(r.isDegraded());
            assertTrue(r.getBasis().contains("折点不完整"));
        }

        @Test
        @DisplayName("降级 5/5: 缺 R 阈值 (空串) → NI, basis 标注折点不完整")
        void degrade_missingRBreakpoint_returnsNi() {
            AntibioticBreakpoint incomplete = micBp(1L, "M100-2023", "≤2", "", null);

            InterpretResult r = interpreter.interpret(micReq("4"), Collections.singletonList(incomplete));

            assertEquals(SirCategory.NI, r.getSir());
            assertTrue(r.getBasis().contains("折点不完整"));
        }

        @Test
        @DisplayName("降级: K-B 缺抑菌圈阈值 → NI, basis 标注 K-B 折点不完整")
        void degrade_missingKbThresholds_returnsNi() {
            AntibioticBreakpoint incomplete = kbBp(1L, "≥18", null);

            InterpretResult r = interpreter.interpret(kbReq("20"), Collections.singletonList(incomplete));

            assertEquals(SirCategory.NI, r.getSir());
            assertTrue(r.getBasis().contains("折点不完整"));
            assertTrue(r.getBasis().contains("K-B"));
        }

        @Test
        @DisplayName("降级: 入参为 null → NI, 不抛异常")
        void degrade_nullRequest_returnsNi() {
            InterpretResult r = assertDoesNotThrow(
                    () -> interpreter.interpret(null, Collections.singletonList(defaultMicBp())));

            assertEquals(SirCategory.NI, r.getSir());
            assertTrue(r.isDegraded());
            assertTrue(r.getBasis().contains("判读入参为空"));
        }

        @Test
        @DisplayName("降级: 候选列表含 null 元素时安全跳过")
        void degrade_candidateListWithNullElement_skipsSafely() {
            List<AntibioticBreakpoint> candidates = new ArrayList<>();
            candidates.add(null);
            candidates.add(defaultMicBp());

            InterpretResult r = assertDoesNotThrow(() -> interpreter.interpret(micReq("1"), candidates));

            assertEquals(SirCategory.S, r.getSir());
        }
    }

    // ==================== 批量判读 ====================

    @Nested
    @DisplayName("批量判读: 结果与入参等长且顺序一致")
    class BatchInterpretation {

        @Test
        @DisplayName("interpretBatch: 按 candidateKey 取折点, 结果顺序与入参一致")
        void interpretBatch_multipleRequests_resultsAlignedWithInputOrder() {
            Map<String, List<AntibioticBreakpoint>> candidateMap = new HashMap<>();
            candidateMap.put(SusceptibilityInterpreter.candidateKey(BACTERIA_ID, ANTIBIOTIC_ID),
                    Collections.singletonList(defaultMicBp()));

            List<InterpretRequest> reqs = Arrays.asList(micReq("1"), micReq("4"), micReq("32"));
            List<InterpretResult> results = interpreter.interpretBatch(reqs, candidateMap);

            assertAll(
                    () -> assertEquals(3, results.size()),
                    () -> assertEquals(SirCategory.S, results.get(0).getSir()),
                    () -> assertEquals(SirCategory.I, results.get(1).getSir()),
                    () -> assertEquals(SirCategory.R, results.get(2).getSir())
            );
        }

        @Test
        @DisplayName("interpretBatch: 折点 Map 缺失对应 key → 该条降级, 不影响其他条目")
        void interpretBatch_missingCandidateKey_degradesOnlyThatEntry() {
            InterpretRequest known = micReq("1");
            InterpretRequest unknown = micReq("1");
            unknown.setAntibioticId(999L);

            Map<String, List<AntibioticBreakpoint>> candidateMap = new HashMap<>();
            candidateMap.put(SusceptibilityInterpreter.candidateKey(BACTERIA_ID, ANTIBIOTIC_ID),
                    Collections.singletonList(defaultMicBp()));

            List<InterpretResult> results = interpreter.interpretBatch(
                    Arrays.asList(known, unknown), candidateMap);

            assertEquals(SirCategory.S, results.get(0).getSir());
            assertEquals(SirCategory.NI, results.get(1).getSir());
            assertTrue(results.get(1).isDegraded());
        }

        @Test
        @DisplayName("interpretBatch: 入参为 null / 空列表 → 返回空列表")
        void interpretBatch_nullOrEmptyRequests_returnsEmptyList() {
            assertTrue(interpreter.interpretBatch(null, new HashMap<>()).isEmpty());
            assertTrue(interpreter.interpretBatch(Collections.emptyList(), new HashMap<>()).isEmpty());
        }

        @Test
        @DisplayName("interpretBatch: candidateMap 为 null → 全部降级, 不抛异常")
        void interpretBatch_nullCandidateMap_allDegraded() {
            List<InterpretResult> results = assertDoesNotThrow(
                    () -> interpreter.interpretBatch(Arrays.asList(micReq("1"), micReq("2")), null));

            assertEquals(2, results.size());
            results.forEach(r -> assertTrue(r.isDegraded()));
        }

        @Test
        @DisplayName("interpretBatch: 列表内含 null 元素 → 该位置返回降级结果, 长度不变")
        void interpretBatch_nullElementInList_keepsLengthWithDegradedResult() {
            List<InterpretRequest> reqs = new ArrayList<>();
            reqs.add(micReq("1"));
            reqs.add(null);

            Map<String, List<AntibioticBreakpoint>> candidateMap = new HashMap<>();
            candidateMap.put(SusceptibilityInterpreter.candidateKey(BACTERIA_ID, ANTIBIOTIC_ID),
                    Collections.singletonList(defaultMicBp()));

            List<InterpretResult> results = interpreter.interpretBatch(reqs, candidateMap);

            assertEquals(2, results.size());
            assertEquals(SirCategory.S, results.get(0).getSir());
            assertTrue(results.get(1).isDegraded());
        }

        @Test
        @DisplayName("candidateKey: 生成规则为 bacteriaId:antibioticId")
        void candidateKey_format_isBacteriaColonAntibiotic() {
            assertEquals("1:10", SusceptibilityInterpreter.candidateKey(1L, 10L));
            assertEquals("null:null", SusceptibilityInterpreter.candidateKey(null, null));
        }
    }

    // ==================== SirCategory ====================

    @Test
    @DisplayName("SirCategory.fromCode: 未知 / null 代码统一返回 NI, 绝不抛异常")
    void fromCode_unknownOrNull_returnsNi() {
        assertAll(
                () -> assertEquals(SirCategory.S, SirCategory.fromCode("S")),
                () -> assertEquals(SirCategory.R, SirCategory.fromCode(" r ")),
                () -> assertEquals(SirCategory.SDD, SirCategory.fromCode("sdd")),
                () -> assertEquals(SirCategory.NI, SirCategory.fromCode(null)),
                () -> assertEquals(SirCategory.NI, SirCategory.fromCode("")),
                () -> assertEquals(SirCategory.NI, SirCategory.fromCode("XYZ"))
        );
    }
}
