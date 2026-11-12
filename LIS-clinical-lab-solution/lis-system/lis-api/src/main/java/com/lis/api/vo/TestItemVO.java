package com.lis.api.vo;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
public class TestItemVO {

    private Long id;

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

    private LocalDateTime createTime;
}
