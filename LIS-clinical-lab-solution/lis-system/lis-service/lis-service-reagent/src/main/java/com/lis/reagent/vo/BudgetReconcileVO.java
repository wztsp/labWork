package com.lis.reagent.vo;

import java.io.Serializable;
import java.math.BigDecimal;

/**
 * 预算对账断言结果（R-03 验收 8）。
 *
 * <p>核心断言：{@code SUM(流水表推算值) == 主表当前值}。
 * 只要出现不一致，说明存在「改了主表没写流水」或「写了流水没改主表」的代码路径，
 * 属于必须立即排查的一致性缺陷。</p>
 *
 * @author LIS Team
 */
public class BudgetReconcileVO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 预算 ID */
    private Long budgetId;
    /** 预算编号 */
    private String budgetNo;
    /** 主表记录的已占用金额 */
    private BigDecimal masterFrozen;
    /** 主表记录的已实扣金额 */
    private BigDecimal masterUsed;
    /** 由流水表推算出的已占用金额 */
    private BigDecimal recordFrozen;
    /** 由流水表推算出的已实扣金额 */
    private BigDecimal recordUsed;
    /** 占用差额 = masterFrozen - recordFrozen，应恒为 0 */
    private BigDecimal frozenDiff;
    /** 实扣差额 = masterUsed - recordUsed，应恒为 0 */
    private BigDecimal usedDiff;
    /** 是否对账一致 */
    private Boolean consistent;
    /** 参与对账的流水条数 */
    private Integer recordCount;
    /** 不一致时的说明文案 */
    private String message;

    public BudgetReconcileVO() {
    }

    public Long getBudgetId() {
        return budgetId;
    }

    public void setBudgetId(Long budgetId) {
        this.budgetId = budgetId;
    }

    public String getBudgetNo() {
        return budgetNo;
    }

    public void setBudgetNo(String budgetNo) {
        this.budgetNo = budgetNo;
    }

    public BigDecimal getMasterFrozen() {
        return masterFrozen;
    }

    public void setMasterFrozen(BigDecimal masterFrozen) {
        this.masterFrozen = masterFrozen;
    }

    public BigDecimal getMasterUsed() {
        return masterUsed;
    }

    public void setMasterUsed(BigDecimal masterUsed) {
        this.masterUsed = masterUsed;
    }

    public BigDecimal getRecordFrozen() {
        return recordFrozen;
    }

    public void setRecordFrozen(BigDecimal recordFrozen) {
        this.recordFrozen = recordFrozen;
    }

    public BigDecimal getRecordUsed() {
        return recordUsed;
    }

    public void setRecordUsed(BigDecimal recordUsed) {
        this.recordUsed = recordUsed;
    }

    public BigDecimal getFrozenDiff() {
        return frozenDiff;
    }

    public void setFrozenDiff(BigDecimal frozenDiff) {
        this.frozenDiff = frozenDiff;
    }

    public BigDecimal getUsedDiff() {
        return usedDiff;
    }

    public void setUsedDiff(BigDecimal usedDiff) {
        this.usedDiff = usedDiff;
    }

    public Boolean getConsistent() {
        return consistent;
    }

    public void setConsistent(Boolean consistent) {
        this.consistent = consistent;
    }

    public Integer getRecordCount() {
        return recordCount;
    }

    public void setRecordCount(Integer recordCount) {
        this.recordCount = recordCount;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }
}
