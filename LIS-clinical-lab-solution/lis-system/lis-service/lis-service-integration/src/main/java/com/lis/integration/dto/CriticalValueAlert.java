package com.lis.integration.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 危急值通知 (回传 HIS)
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CriticalValueAlert {

    private String patientId;

    private String patientName;

    private String sampleBarcode;

    private String itemCode;

    private String itemName;

    private String resultValue;

    private String unit;

    private String refRange;

    private LocalDateTime alertTime;

    private String alertSource;

    private String remark;
}
