package com.lis.reagent.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 试剂预算变动流水（只增不改不删）。
 *
 * <p>{@code amount} 恒为正，方向由 {@code changeType} 决定
 * （见 {@code com.lis.reagent.enums.ChangeType}）。</p>
 *
 * <p>唯一索引 {@code (budget_id, change_type, source_type, source_id)} 提供天然幂等：
 * 同一单据对同一预算的同一种变动只能发生一次，重复提交会撞唯一约束触发事务回滚，
 * 无需在代码里判重。</p>
 *
 * <p>Service 层只提供 insert，不提供 update/delete；Controller 不暴露写端点（吸取缺陷 O 教训）。</p>
 *
 * <p>手写 getter/setter，不使用 Lombok（C-10）。不声明 {@code deleted} 字段（C-9 复核结论）。</p>
 */
@TableName("lis_reagent_budget_record")
public class ReagentBudgetRecord {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long budgetId;
    /** 1占用 2释放 3实扣 4调增 5调减 */
    private Integer changeType;
    /** 变动金额，恒为正 */
    private BigDecimal amount;
    private BigDecimal beforeFrozen;
    private BigDecimal afterFrozen;
    private BigDecimal beforeUsed;
    private BigDecimal afterUsed;
    /** APPLY / ORDER / INBOUND / MANUAL */
    private String sourceType;
    /** 来源单据ID */
    private Long sourceId;
    /** 来源单号 */
    private String sourceNo;
    private Long operatorId;
    private String operatorName;
    private String remark;
    private LocalDateTime createTime;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getBudgetId() {
        return budgetId;
    }

    public void setBudgetId(Long budgetId) {
        this.budgetId = budgetId;
    }

    public Integer getChangeType() {
        return changeType;
    }

    public void setChangeType(Integer changeType) {
        this.changeType = changeType;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public void setAmount(BigDecimal amount) {
        this.amount = amount;
    }

    public BigDecimal getBeforeFrozen() {
        return beforeFrozen;
    }

    public void setBeforeFrozen(BigDecimal beforeFrozen) {
        this.beforeFrozen = beforeFrozen;
    }

    public BigDecimal getAfterFrozen() {
        return afterFrozen;
    }

    public void setAfterFrozen(BigDecimal afterFrozen) {
        this.afterFrozen = afterFrozen;
    }

    public BigDecimal getBeforeUsed() {
        return beforeUsed;
    }

    public void setBeforeUsed(BigDecimal beforeUsed) {
        this.beforeUsed = beforeUsed;
    }

    public BigDecimal getAfterUsed() {
        return afterUsed;
    }

    public void setAfterUsed(BigDecimal afterUsed) {
        this.afterUsed = afterUsed;
    }

    public String getSourceType() {
        return sourceType;
    }

    public void setSourceType(String sourceType) {
        this.sourceType = sourceType;
    }

    public Long getSourceId() {
        return sourceId;
    }

    public void setSourceId(Long sourceId) {
        this.sourceId = sourceId;
    }

    public String getSourceNo() {
        return sourceNo;
    }

    public void setSourceNo(String sourceNo) {
        this.sourceNo = sourceNo;
    }

    public Long getOperatorId() {
        return operatorId;
    }

    public void setOperatorId(Long operatorId) {
        this.operatorId = operatorId;
    }

    public String getOperatorName() {
        return operatorName;
    }

    public void setOperatorName(String operatorName) {
        this.operatorName = operatorName;
    }

    public String getRemark() {
        return remark;
    }

    public void setRemark(String remark) {
        this.remark = remark;
    }

    public LocalDateTime getCreateTime() {
        return createTime;
    }

    public void setCreateTime(LocalDateTime createTime) {
        this.createTime = createTime;
    }

}
