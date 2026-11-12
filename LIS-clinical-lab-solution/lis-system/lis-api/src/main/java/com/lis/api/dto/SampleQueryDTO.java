package com.lis.api.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SampleQueryDTO {

    private String sampleBarcode;

    /** 就诊号查询 */
    private String visitNo;

    private String patientName;

    private Integer status;

    private Integer isUrgent;

    private String department;

    private String startDate;

    private String endDate;

    /** 签收日期范围 (独立于采集日期) */
    private String signOffStartDate;

    private String signOffEndDate;

    /** 送检日期范围 */
    private String deliverStartDate;

    private String deliverEndDate;

    /** 申请单ID查询 */
    private Long orderId;

    /** 就诊ID查询 (精确匹配, 前端标本采集页面按就诊维度使用) */
    private Long visitId;

    /** 就诊ID列表查询 (当查询匹配多就诊时, 合并加载) */
    private java.util.List<Long> visitIds;

    /** 采集人ID查询 */
    private Long collectorId;

    /** 送检人查询 */
    private String deliverPerson;

    /** 签收人查询 */
    private String signOffPerson;

    @Builder.Default
    private long current = 1L;

    @Builder.Default
    private long size = 10L;
}
