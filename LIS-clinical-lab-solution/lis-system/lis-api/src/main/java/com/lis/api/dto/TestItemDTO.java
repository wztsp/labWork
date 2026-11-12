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
public class TestItemDTO {

    private String itemCode;

    private String itemName;

    private String itemNameEn;

    private String shortName;

    private String category;

    private String specimenType;

    private String unit;

    private String method;

    private Integer isPanel;

    private Long parentId;

    private BigDecimal price;

    private Integer status;
}
