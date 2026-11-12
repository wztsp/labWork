package com.lis.integration.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 检验报告 (回传 HIS)
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LabReport {

    private String reportNo;

    private Long sampleId;

    private String patientId;

    private String patientName;

    private String reportType;

    private String reportDoctor;

    private String checker;

    private LocalDateTime publishTime;

    /** 结果项列表 (key=itemCode, value=resultValue|unit|refRange|abnormalFlag) */
    private List<ResultItem> results;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ResultItem {
        private String itemCode;
        private String itemName;
        private String resultValue;
        private String unit;
        private String refRange;
        private String abnormalFlag;
    }
}
