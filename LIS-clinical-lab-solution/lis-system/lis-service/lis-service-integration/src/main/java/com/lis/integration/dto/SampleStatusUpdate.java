package com.lis.integration.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 标本状态更新 (回传 HIS)
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SampleStatusUpdate {

    /** HIS 申请单号 */
    private String orderNo;

    /** 标本条码 */
    private String sampleBarcode;

    /** 标本状态: COLLECTED=已采集 RECEIVED=已核收 TESTING=检测中 COMPLETED=已完成 REJECTED=已拒收 */
    private String status;

    private LocalDateTime updateTime;

    private String operatorName;

    private String remark;
}
