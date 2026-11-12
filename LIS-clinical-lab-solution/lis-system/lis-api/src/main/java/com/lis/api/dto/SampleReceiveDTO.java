package com.lis.api.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SampleReceiveDTO {

    private String sampleBarcode;

    /** 核收仪器ID (必填, 决定标本号归属仪器并校验仪器能否检测该项目) */
    private Long instrumentId;

    private Long operatorId;

    private String department;

    private String remark;
}
