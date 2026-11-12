package com.lis.reagent.dto;

import java.io.Serializable;
import java.math.BigDecimal;

/**
 * 盘点明细实盘录入入参（单条）。
 *
 * <p>由 {@code StockCheckController.submitItems()} 批量接收，
 * Service 逐条 {@code StockCheckCalculator.diff(actualQty, bookQty)} 后调账。</p>
 *
 * @author LIS Team
 */
public class ItemSubmitDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 盘点明细 ID，必填 */
    private Long itemId;
    /** 实盘数量，必填且不得为负 */
    private BigDecimal actualQty;
    /** 备注（差异说明） */
    private String remark;
    /** 差异原因 */
    private String diffReason;

    public ItemSubmitDTO() {
    }

    public Long getItemId() {
        return itemId;
    }

    public void setItemId(Long itemId) {
        this.itemId = itemId;
    }

    public BigDecimal getActualQty() {
        return actualQty;
    }

    public void setActualQty(BigDecimal actualQty) {
        this.actualQty = actualQty;
    }

    public String getRemark() {
        return remark;
    }

    public void setRemark(String remark) {
        this.remark = remark;
    }

    public String getDiffReason() {
        return diffReason;
    }

    public void setDiffReason(String diffReason) {
        this.diffReason = diffReason;
    }
}
