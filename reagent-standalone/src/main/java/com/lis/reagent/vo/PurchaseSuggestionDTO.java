package com.lis.reagent.vo;

import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 试剂采购建议。
 */
@Data
public class PurchaseSuggestionDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 当前库存（未过期且可用批次汇总） */
    private BigDecimal currentStock;

    /** 库存下限 */
    private BigDecimal minStock;

    /** 库存上限 */
    private BigDecimal maxStock;

    /** 最佳库存 */
    private BigDecimal optimalStock;

    /** 采购周期(天) */
    private Integer purchaseCycleDays;

    /** 建议采购数量 = 最佳库存(无则最高库存) - 当前库存，小于 0 取 0 */
    private BigDecimal suggestedQty;

    /** 建议采购日期 = 今天 + 采购周期天数 */
    private LocalDate suggestedDate;
}
