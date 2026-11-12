package com.lis.integration.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;

/**
 * 费用账单 (回传 HIS 计费)
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FeeBilling {

    /** 订单号 */
    private String orderId;

    /** 患者ID */
    private String patientId;

    /** 总费用 */
    private BigDecimal totalFee;

    /** 费用明细项 */
    private List<FeeItem> items;
}
