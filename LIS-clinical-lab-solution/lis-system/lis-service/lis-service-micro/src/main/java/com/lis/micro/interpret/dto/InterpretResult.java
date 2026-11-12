package com.lis.micro.interpret.dto;

import com.lis.micro.interpret.SirCategory;
import lombok.Data;

import java.math.BigDecimal;

/**
 * 药敏判读出参
 */
@Data
public class InterpretResult {

    /** 判读类别: S/I/SDD/R/NI */
    private SirCategory sir;

    /** 原始测定值, 原样回传 */
    private String rawValue;

    /** 原始值解析出的数值; 非数值时为 null */
    private BigDecimal numericValue;

    /** 命中的折点记录ID; 未命中为 null */
    private Long matchedBreakpointId;

    /** 实际使用的标准 */
    private String standard;

    /** 实际使用的标准版本 */
    private String standardVersion;

    /** 判读依据文本 (含降级原因) */
    private String basis;

    /** 是否降级: true 表示无法正常判读, sir 为 NI 或取自人工值 */
    private boolean degraded;

    /** 附加说明 */
    private String message;

    /** 业务侧回传标识, 与入参 refKey 对应 */
    private String refKey;

    /**
     * 构造正常判读结果。
     *
     * @param sir          判读类别
     * @param req          原始请求, 可为 null
     * @param numericValue 解析出的数值
     * @param basis        判读依据
     */
    public static InterpretResult of(SirCategory sir, InterpretRequest req,
                                     BigDecimal numericValue, String basis) {
        InterpretResult r = new InterpretResult();
        r.setSir(sir != null ? sir : SirCategory.NI);
        r.setNumericValue(numericValue);
        r.setBasis(basis);
        r.setDegraded(false);
        if (req != null) {
            r.setRawValue(req.getRawValue());
            r.setStandard(req.resolveStandard());
            r.setStandardVersion(req.getStandardVersion());
            r.setRefKey(req.getRefKey());
        }
        return r;
    }

    /**
     * 构造降级结果: sir=NI, degraded=true。
     *
     * @param req   原始请求, 可为 null
     * @param basis 降级原因
     */
    public static InterpretResult degraded(InterpretRequest req, String basis) {
        InterpretResult r = of(SirCategory.NI, req, null, basis);
        r.setDegraded(true);
        r.setMessage("无法判读, 已降级返回 NI, 不阻断报告流程");
        return r;
    }
}
