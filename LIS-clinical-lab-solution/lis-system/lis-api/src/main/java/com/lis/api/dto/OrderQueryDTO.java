package com.lis.api.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 检验申请查询 DTO
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OrderQueryDTO {

    /** 就诊号查询 */
    private String visitNo;

    /** 患者姓名模糊查询 */
    private String patientName;

    /** 申请单号查询 */
    private String orderNo;

    /** 申请状态 */
    private Integer status;

    @Builder.Default
    private long current = 1L;

    @Builder.Default
    private long size = 20L;
}
