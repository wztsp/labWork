package com.lis.report.dto;

import lombok.Data;

import java.util.List;
import java.util.Map;

/**
 * 批量修改结果 DTO
 */
@Data
public class BatchModifyResultDTO {
    /** 标本 ID */
    private List<Long> sampleIds;
    /**
     * 作用范围
     * all       - 该标本下所有指标
     * abnormal  - 仅异常指标
     * panel     - 指定检验项目（需指定 panelIds）
     * indicators- 指定指标（需指定 indicatorCodes）
     */
    private String scope;
    /** 指定的检验项目 ID（scope=panel 时必填） */
    private List<Long> panelIds;
    /** 指定的指标代码（scope=indicators 时必填） */
    private List<String> indicatorCodes;
    /**
     * 修改模式
     * fixed     - 固定值（需指定 fixedValue）
     * offset    - 偏移量（需指定 offsetValue，支持数值：0.2 或百分比：10%）
     * factor    - 系数（需指定 factorValue，如 1.1）
     * formula   - 自定义表达式（暂不支持）
     */
    private String mode;
    /** fixed 模式下的固定值 */
    private String fixedValue;
    /** offset 模式下的偏移量：数字(例如 0.1) 或 百分比字符串(例如 "10%") */
    private String offsetValue;
    /** factor 模式下的系数，例如 1.1 */
    private Double factorValue;
    /** 是否允许空值被设置 */
    private Boolean allowEmpty;
    /**
     * 逐条覆盖（可选，优先级最高）
     * key: sampleId, value: Map<code, resultValue>
     */
    private Map<Long, Map<String, Object>> perSample;
}
