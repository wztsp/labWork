package com.lis.api.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ReferenceRangeDTO {

    private Long testItemId;

    private Integer gender;

    private Integer ageMin;

    private Integer ageMax;

    private BigDecimal refLow;

    private BigDecimal refHigh;

    private String refText;

    private BigDecimal criticalLow;

    private BigDecimal criticalHigh;

    private String unit;

    private String conditionRemark;
}
