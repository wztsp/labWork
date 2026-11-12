package com.lis.report.dto;

import lombok.Data;

import java.util.List;

/**
 * 批量修改检验人 DTO
 */
@Data
public class BatchModifyInspectorDTO {
    /** 标本 ID */
    private List<Long> sampleIds;
    /** 检验人姓名 */
    private String inspector;
    /** 检验人用户 ID（可选） */
    private Long inspectorUserId;
    /** 修改模式：当前仅支持覆盖 overwrite */
    private String mode;
}
