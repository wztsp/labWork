package com.lis.api.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 标本拒收 DTO
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SampleRejectDTO {

    /** 标本条码 */
    private String barcode;

    /** 不合格类型 */
    private String rejectType;

    /** 拒收原因 */
    private String rejectReason;

    /** 拒收人姓名 */
    private String operator;

    /** 拒收人ID (关联人事员工 hr_staff.id, 优先使用) */
    private Long operatorId;
}
