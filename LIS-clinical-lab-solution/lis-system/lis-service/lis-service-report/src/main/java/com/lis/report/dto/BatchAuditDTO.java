package com.lis.report.dto;

import lombok.Data;

import java.util.List;
import java.util.Map;

/**
 * 批量审核 DTO
 */
@Data
public class BatchAuditDTO {
    /** 标本 ID 列表 */
    private List<Long> sampleIds;
    /** 是否通过审核 */
    private Boolean pass;
    /** 原因（驳回/让步时使用） */
    private String reason;
    /**
     * 逐条覆盖配置（可选，优先级高于 pass/reason）
     * key: sampleId value: { pass, reason }
     */
    private Map<Long, Map<String, Object>> perSample;
}
