package com.lis.api.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 标本批量操作 DTO (送检/签收)
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SampleBatchDTO {

    /** 标本条码列表 */
    private List<String> barcodes;

    /** 操作人 (送检人/签收人) 姓名 */
    private String operator;

    /** 操作人ID (关联人事员工 hr_staff.id, 优先使用) */
    private Long operatorId;
}
