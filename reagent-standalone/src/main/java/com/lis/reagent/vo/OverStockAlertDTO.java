package com.lis.reagent.vo;

import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;

/**
 * 高库存预警行。
 */
@Data
public class OverStockAlertDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 试剂 ID */
    private Long reagentId;

    /** 试剂编码 */
    private String reagentCode;

    /** 试剂名称 */
    private String reagentName;

    /** 当前可用库存合计 */
    private BigDecimal currentQty;

    /** 库存上限阈值 */
    private BigDecimal maxStock;

    /** 超储量 = currentQty - maxStock，恒为正数 */
    private BigDecimal excessQty;
}
