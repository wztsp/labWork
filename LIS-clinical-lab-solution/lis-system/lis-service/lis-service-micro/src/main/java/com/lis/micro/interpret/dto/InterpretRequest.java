package com.lis.micro.interpret.dto;

import lombok.Data;

/**
 * 药敏判读入参
 */
@Data
public class InterpretRequest {

    /** 默认标准 */
    public static final String DEFAULT_STANDARD = "CLSI";
    /** 方法学: 最低抑菌浓度法 */
    public static final String METHOD_MIC = "MIC";
    /** 方法学: 纸片扩散法(抑菌圈直径) */
    public static final String METHOD_KB = "K-B";

    /** 细菌ID, 关联 lis_bacteria.id */
    private Long bacteriaId;

    /** 抗生素ID, 关联 lis_antibiotic.id */
    private Long antibioticId;

    /** 方法学: MIC / K-B; 为空时按 MIC 处理 */
    private String method;

    /** 原始测定值, 如 "≤0.5" / "4" / "18"; 非数值 (如 "NG") 将降级 */
    private String rawValue;

    /** 判读标准, 为空时默认 CLSI */
    private String standard;

    /** 标准版本, 如 M100-2023; 为空表示自动取最新版本 */
    private String standardVersion;

    /**
     * 人工判读结果 (S/I/SDD/R)。非空时人工值优先，引擎结果不覆盖人工值。
     */
    private String manualResult;

    /** 业务侧回传标识, 引擎不使用, 原样返回便于批量结果对齐 */
    private String refKey;

    /** 取标准, 为空返回默认 CLSI */
    public String resolveStandard() {
        return (standard == null || standard.trim().isEmpty()) ? DEFAULT_STANDARD : standard.trim();
    }

    /** 取方法学, 为空返回默认 MIC */
    public String resolveMethod() {
        return (method == null || method.trim().isEmpty()) ? METHOD_MIC : method.trim();
    }
}
