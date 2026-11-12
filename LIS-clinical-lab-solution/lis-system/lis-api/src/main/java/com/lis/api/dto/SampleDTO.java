package com.lis.api.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SampleDTO {

    private String sampleBarcode;

    private Long orderId;

    private Long patientId;

    /** 患者姓名 (冗余, 便于查询展示) */
    private String patientName;

    private Long visitId;

    /** 就诊号 (冗余, 便于采集工作站通过就诊号查询标本) */
    private String visitNo;

    private String sampleType;

    private String containerType;

    private Integer isUrgent;

    private String department;

    private String remark;

    private LocalDateTime collectTime;
}
