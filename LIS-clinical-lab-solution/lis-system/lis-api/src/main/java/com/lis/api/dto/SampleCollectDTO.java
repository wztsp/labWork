package com.lis.api.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 标本采集 DTO
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SampleCollectDTO {

    /** 标本条码 (与sampleId二选一) */
    private String sampleBarcode;

    /** 标本ID (与sampleBarcode二选一) */
    private Long sampleId;

    /** 采集人ID (若为空则使用当前登录用户) */
    private Long collectorId;

    /** 采集时间 (若为空则使用当前系统时间) */
    private String collectTime;

    /** 采集备注 */
    private String remark;
}
