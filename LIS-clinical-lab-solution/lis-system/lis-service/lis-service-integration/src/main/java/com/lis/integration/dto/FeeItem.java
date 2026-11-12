package com.lis.integration.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * 费用明细项
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FeeItem {

    /** 检验项目ID */
    private Long testItemId;

    /** 检验项目名称 */
    private String testItemName;

    /** 单价 */
    private BigDecimal fee;

    /** 数量 */
    private Integer quantity;
}
