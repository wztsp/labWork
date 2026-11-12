package com.lis.micro.interpret.impl;

import com.lis.micro.entity.AntibioticBreakpoint;
import com.lis.micro.interpret.BreakpointExpr;
import com.lis.micro.interpret.SirCategory;
import com.lis.micro.interpret.SusceptibilityInterpreter;
import com.lis.micro.interpret.dto.InterpretRequest;
import com.lis.micro.interpret.dto.InterpretResult;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

/**
 * CLSI 药敏判读算法实现。
 *
 * <p><b>零 Spring 依赖的纯 POJO</b>，可直接 {@code new} 出来做单元测试，
 * 无需任何 mock 与容器上下文。</p>
 *
 * <p>判读流程：解析原始值 → 选择折点（status/standard/method 过滤 + 版本降序取最新）
 * → 按方法学分派 MIC / K-B 判定。任一环节无法继续时降级为
 * {@link SirCategory#NI} 并给出 basis，绝不抛异常。</p>
 */
public class ClsiSusceptibilityInterpreter implements SusceptibilityInterpreter {

    /** 归一化后的 K-B 方法学标识 */
    private static final String M_KB = "K-B";
    /** 归一化后的 MIC 方法学标识 */
    private static final String M_MIC = "MIC";

    @Override
    public InterpretResult interpret(InterpretRequest req, List<AntibioticBreakpoint> candidates) {
        if (req == null) {
            return InterpretResult.degraded(null, "判读入参为空");
        }

        // 1. 解析原始值 (先于折点检查: 非数值原始值与折点配置无关)
        BreakpointExpr rawExpr = BreakpointExpr.parse(req.getRawValue());
        if (!rawExpr.isValid()) {
            return InterpretResult.degraded(req, "原始值非数值: '" + req.getRawValue() + "'");
        }
        BigDecimal value = rawExpr.getValue();

        // 2. 选择折点
        AntibioticBreakpoint bp = selectBreakpoint(req, candidates);
        if (bp == null) {
            return InterpretResult.degraded(req, String.format(
                    "未配置%s折点(bacteriaId=%s, antibioticId=%s, method=%s%s)",
                    req.resolveStandard(), req.getBacteriaId(), req.getAntibioticId(), req.resolveMethod(),
                    isBlank(req.getStandardVersion()) ? "" : ", version=" + req.getStandardVersion()));
        }

        // 3. 按方法学分派
        InterpretResult result = M_KB.equals(normalizeMethod(req.resolveMethod()))
                ? interpretDiskDiffusion(req, rawExpr, value, bp)
                : interpretMic(req, rawExpr, value, bp);

        // 4. 回填命中的折点信息
        result.setMatchedBreakpointId(bp.getId());
        if (!isBlank(bp.getStandard())) {
            result.setStandard(bp.getStandard());
        }
        if (!isBlank(bp.getStandardVersion())) {
            result.setStandardVersion(bp.getStandardVersion());
        }
        return result;
    }

    @Override
    public List<InterpretResult> interpretBatch(List<InterpretRequest> reqs,
                                                Map<String, List<AntibioticBreakpoint>> candidateMap) {
        List<InterpretResult> results = new ArrayList<>();
        if (reqs == null || reqs.isEmpty()) {
            return results;
        }
        for (InterpretRequest req : reqs) {
            if (req == null) {
                results.add(InterpretResult.degraded(null, "判读入参为空"));
                continue;
            }
            List<AntibioticBreakpoint> candidates = candidateMap == null
                    ? Collections.emptyList()
                    : candidateMap.get(SusceptibilityInterpreter.candidateKey(req.getBacteriaId(), req.getAntibioticId()));
            results.add(interpret(req, candidates));
        }
        return results;
    }

    // ==================== 折点选择 ====================

    /**
     * 从候选折点中挑选唯一确定的一条。
     *
     * <p>过滤规则: status != 0（null 视为启用）→ standard 匹配（折点未填标准视为通配）
     * → method 匹配（折点未填方法学视为通配）→ 请求指定版本时按版本精确过滤。
     * 排序规则: standardVersion 字典序<b>降序</b>（M100-2023 &gt; M100-2021，null 排最后），
     * 同版本按 id 升序，保证判读结果确定性可复现。</p>
     *
     * @return 命中的折点; 无候选时返回 null
     */
    public AntibioticBreakpoint selectBreakpoint(InterpretRequest req, List<AntibioticBreakpoint> candidates) {
        if (req == null || candidates == null || candidates.isEmpty()) {
            return null;
        }
        String wantStandard = req.resolveStandard();
        String wantMethod = normalizeMethod(req.resolveMethod());
        String wantVersion = req.getStandardVersion();

        List<AntibioticBreakpoint> matched = new ArrayList<>();
        for (AntibioticBreakpoint bp : candidates) {
            if (bp == null) {
                continue;
            }
            // status: 明确为非 1 的记录判定为停用
            if (bp.getStatus() != null && bp.getStatus() != 1) {
                continue;
            }
            if (!isBlank(bp.getStandard()) && !bp.getStandard().trim().equalsIgnoreCase(wantStandard)) {
                continue;
            }
            if (!isBlank(bp.getMethod()) && !normalizeMethod(bp.getMethod()).equals(wantMethod)) {
                continue;
            }
            if (!isBlank(wantVersion)
                    && (isBlank(bp.getStandardVersion())
                        || !bp.getStandardVersion().trim().equalsIgnoreCase(wantVersion.trim()))) {
                continue;
            }
            matched.add(bp);
        }
        if (matched.isEmpty()) {
            return null;
        }
        matched.sort(Comparator
                .<AntibioticBreakpoint, String>comparing(AntibioticBreakpoint::getStandardVersion,
                        Comparator.nullsLast(Comparator.<String>reverseOrder()))
                .thenComparing(AntibioticBreakpoint::getId,
                        Comparator.nullsLast(Comparator.<Long>naturalOrder())));
        return matched.get(0);
    }

    /**
     * 方法学归一化: 把库内中文方法学名统一到 MIC / K-B 两类。
     *
     * @param method 原始方法学串
     * @return {@code "K-B"} / {@code "MIC"} / 原串大写形式
     */
    public static String normalizeMethod(String method) {
        if (method == null) {
            return M_MIC;
        }
        String m = method.trim();
        if (m.isEmpty()) {
            return M_MIC;
        }
        String upper = m.toUpperCase();
        if (upper.contains("K-B") || upper.contains("KB") || upper.contains("DISK")
                || upper.contains("DIFFUSION") || m.contains("纸片") || m.contains("抑菌圈")) {
            return M_KB;
        }
        if (upper.contains("MIC") || upper.contains("E-TEST") || upper.contains("ETEST")
                || upper.contains("BROTH") || m.contains("肉汤") || m.contains("微量")
                || m.contains("最低抑菌浓度")) {
            return M_MIC;
        }
        return upper;
    }

    // ==================== MIC 判定 ====================

    /**
     * MIC 法判定：数值越小越敏感。
     *
     * <p>{@code value ≤ S阈值} → S；{@code value ≥ R阈值} → R；落中间 → I。
     * 边界仲裁：原始值带 {@code ≥ / >} 前缀且恰等 R 阈值时优先判 R。</p>
     */
    private InterpretResult interpretMic(InterpretRequest req, BreakpointExpr rawExpr,
                                         BigDecimal value, AntibioticBreakpoint bp) {
        String sRaw = bp.getSBreakpoint();
        String rRaw = bp.getRBreakpoint();
        if (isBlank(sRaw) || isBlank(rRaw)) {
            return InterpretResult.degraded(req, "折点不完整: 缺少 S/R 阈值(sBreakpoint="
                    + sRaw + ", rBreakpoint=" + rRaw + ")");
        }
        BreakpointExpr sExpr = BreakpointExpr.parse(sRaw);
        if (!sExpr.isValid()) {
            return InterpretResult.degraded(req, "折点表达式非法: '" + sRaw + "'");
        }
        BreakpointExpr rExpr = BreakpointExpr.parse(rRaw);
        if (!rExpr.isValid()) {
            return InterpretResult.degraded(req, "折点表达式非法: '" + rRaw + "'");
        }

        BigDecimal sVal = sExpr.getValue();
        BigDecimal rVal = rExpr.getValue();
        int cmpS = value.compareTo(sVal);
        int cmpR = value.compareTo(rVal);
        String range = "S" + sExpr.getRaw() + " / R" + rExpr.getRaw();

        // 边界仲裁: 原始值为下界型 (≥/>) 且已达到 R 阈值 → 直接判 R
        if (rawExpr.isLowerBound() && cmpR >= 0) {
            return InterpretResult.of(SirCategory.R, req, value,
                    "MIC法: 原始值" + rawExpr.getRaw() + " 不低于耐药阈值 " + rVal.toPlainString()
                            + " (" + range + ")");
        }
        if (cmpS <= 0) {
            return InterpretResult.of(SirCategory.S, req, value,
                    "MIC法: " + value.toPlainString() + " ≤ 敏感阈值 " + sVal.toPlainString()
                            + " (" + range + ")");
        }
        if (cmpR >= 0) {
            return InterpretResult.of(SirCategory.R, req, value,
                    "MIC法: " + value.toPlainString() + " ≥ 耐药阈值 " + rVal.toPlainString()
                            + " (" + range + ")");
        }

        // 落中间区: 优先使用显式配置的中介折点作为依据
        BreakpointExpr iExpr = BreakpointExpr.parse(bp.getIBreakpoint());
        if (iExpr.isValid() && iExpr.matches(value)) {
            return InterpretResult.of(SirCategory.I, req, value,
                    "MIC法: " + value.toPlainString() + " 命中中介折点 " + iExpr.getRaw()
                            + " (" + range + ")");
        }
        return InterpretResult.of(SirCategory.I, req, value,
                "MIC法: " + value.toPlainString() + " 介于敏感阈值 " + sVal.toPlainString()
                        + " 与耐药阈值 " + rVal.toPlainString() + " 之间, 推定中介 (" + range + ")");
    }

    // ==================== K-B 判定 ====================

    /**
     * K-B 纸片扩散法判定：抑菌圈直径<b>越大越敏感</b>，方向与 MIC 相反。
     *
     * <p>{@code value ≥ s_dd} → S；{@code value ≤ r_dd} → R；落中间 → I。</p>
     */
    private InterpretResult interpretDiskDiffusion(InterpretRequest req, BreakpointExpr rawExpr,
                                                   BigDecimal value, AntibioticBreakpoint bp) {
        String sRaw = bp.getSDd();
        String rRaw = bp.getRDd();
        if (isBlank(sRaw) || isBlank(rRaw)) {
            return InterpretResult.degraded(req, "折点不完整: 缺少 K-B 抑菌圈阈值(sDd="
                    + sRaw + ", rDd=" + rRaw + ")");
        }
        BreakpointExpr sExpr = BreakpointExpr.parse(sRaw);
        if (!sExpr.isValid()) {
            return InterpretResult.degraded(req, "折点表达式非法: '" + sRaw + "'");
        }
        BreakpointExpr rExpr = BreakpointExpr.parse(rRaw);
        if (!rExpr.isValid()) {
            return InterpretResult.degraded(req, "折点表达式非法: '" + rRaw + "'");
        }

        BigDecimal sVal = sExpr.getValue();
        BigDecimal rVal = rExpr.getValue();
        String range = "S" + sExpr.getRaw() + " / R" + rExpr.getRaw();

        if (value.compareTo(sVal) >= 0) {
            return InterpretResult.of(SirCategory.S, req, value,
                    "K-B法: 抑菌圈 " + value.toPlainString() + "mm ≥ 敏感阈值 "
                            + sVal.toPlainString() + "mm (" + range + ")");
        }
        if (value.compareTo(rVal) <= 0) {
            return InterpretResult.of(SirCategory.R, req, value,
                    "K-B法: 抑菌圈 " + value.toPlainString() + "mm ≤ 耐药阈值 "
                            + rVal.toPlainString() + "mm (" + range + ")");
        }
        return InterpretResult.of(SirCategory.I, req, value,
                "K-B法: 抑菌圈 " + value.toPlainString() + "mm 介于耐药阈值 "
                        + rVal.toPlainString() + "mm 与敏感阈值 " + sVal.toPlainString()
                        + "mm 之间, 判定中介 (" + range + ")");
    }

    // ==================== 工具方法 ====================

    private static boolean isBlank(String s) {
        return s == null || s.trim().isEmpty();
    }
}
