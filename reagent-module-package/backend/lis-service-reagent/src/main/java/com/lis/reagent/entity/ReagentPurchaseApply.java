package com.lis.reagent.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 试剂采购申请单。
 *
 * <p>状态取值见 {@code com.lis.reagent.enums.ApplyStatus}，流转由纯 POJO
 * {@code PurchaseApplyStateMachine} 约束。一张申请只挂一个预算（budgetId），
 * 明细类别必须收敛到同一预算三元组，否则要求拆单（见设计文档 §2.2.4，规避跨预算死锁）。</p>
 *
 * <p>手写 getter/setter，不使用 Lombok（C-10）。不声明 {@code deleted} 字段（C-9 复核结论）。</p>
 */
@TableName("lis_reagent_purchase_apply")
public class ReagentPurchaseApply {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 申请单号 PA + yyyyMMdd + 4位序列，唯一 */
    private String applyNo;
    /** 申请科室（sys_department.id 快照） */
    private Long applyDeptId;
    private String applyDeptName;
    private Long applyUserId;
    private String applyUserName;
    /** 命中的预算ID（一申请一预算） */
    private Long budgetId;
    private Integer budgetYear;
    /** 本申请收敛到的试剂类别 */
    private Long categoryId;
    /** 明细汇总金额 */
    private BigDecimal totalAmount;
    /** 0草稿 1待审批 2审批中 3已批准 4已驳回 5已冻结 6已作废 */
    private Integer status;
    /** 当前审批到第几级，0=未进入审批 */
    private Integer currentNodeLevel;
    /** 本次解析出的审批节点总数 */
    private Integer totalNodeCount;
    /** 0正常 1超预算（冻结原因） */
    private Integer overBudgetFlag;
    /** 超额金额（供页面明示，R-03 验收 7） */
    private BigDecimal excessAmount;
    /** 0普通 1紧急 */
    private Integer urgentFlag;
    /** 期望到货日期 */
    private LocalDate expectDate;
    private LocalDateTime submitTime;
    private LocalDateTime finishTime;
    private String remark;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;

    /** 明细列表（非数据库字段，详情组装用） */
    @TableField(exist = false)
    private List<ReagentPurchaseApplyItem> items;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getApplyNo() {
        return applyNo;
    }

    public void setApplyNo(String applyNo) {
        this.applyNo = applyNo;
    }

    public Long getApplyDeptId() {
        return applyDeptId;
    }

    public void setApplyDeptId(Long applyDeptId) {
        this.applyDeptId = applyDeptId;
    }

    public String getApplyDeptName() {
        return applyDeptName;
    }

    public void setApplyDeptName(String applyDeptName) {
        this.applyDeptName = applyDeptName;
    }

    public Long getApplyUserId() {
        return applyUserId;
    }

    public void setApplyUserId(Long applyUserId) {
        this.applyUserId = applyUserId;
    }

    public String getApplyUserName() {
        return applyUserName;
    }

    public void setApplyUserName(String applyUserName) {
        this.applyUserName = applyUserName;
    }

    public Long getBudgetId() {
        return budgetId;
    }

    public void setBudgetId(Long budgetId) {
        this.budgetId = budgetId;
    }

    public Integer getBudgetYear() {
        return budgetYear;
    }

    public void setBudgetYear(Integer budgetYear) {
        this.budgetYear = budgetYear;
    }

    public Long getCategoryId() {
        return categoryId;
    }

    public void setCategoryId(Long categoryId) {
        this.categoryId = categoryId;
    }

    public BigDecimal getTotalAmount() {
        return totalAmount;
    }

    public void setTotalAmount(BigDecimal totalAmount) {
        this.totalAmount = totalAmount;
    }

    public Integer getStatus() {
        return status;
    }

    public void setStatus(Integer status) {
        this.status = status;
    }

    public Integer getCurrentNodeLevel() {
        return currentNodeLevel;
    }

    public void setCurrentNodeLevel(Integer currentNodeLevel) {
        this.currentNodeLevel = currentNodeLevel;
    }

    public Integer getTotalNodeCount() {
        return totalNodeCount;
    }

    public void setTotalNodeCount(Integer totalNodeCount) {
        this.totalNodeCount = totalNodeCount;
    }

    public Integer getOverBudgetFlag() {
        return overBudgetFlag;
    }

    public void setOverBudgetFlag(Integer overBudgetFlag) {
        this.overBudgetFlag = overBudgetFlag;
    }

    public BigDecimal getExcessAmount() {
        return excessAmount;
    }

    public void setExcessAmount(BigDecimal excessAmount) {
        this.excessAmount = excessAmount;
    }

    public Integer getUrgentFlag() {
        return urgentFlag;
    }

    public void setUrgentFlag(Integer urgentFlag) {
        this.urgentFlag = urgentFlag;
    }

    public LocalDate getExpectDate() {
        return expectDate;
    }

    public void setExpectDate(LocalDate expectDate) {
        this.expectDate = expectDate;
    }

    public LocalDateTime getSubmitTime() {
        return submitTime;
    }

    public void setSubmitTime(LocalDateTime submitTime) {
        this.submitTime = submitTime;
    }

    public LocalDateTime getFinishTime() {
        return finishTime;
    }

    public void setFinishTime(LocalDateTime finishTime) {
        this.finishTime = finishTime;
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

    public LocalDateTime getUpdateTime() {
        return updateTime;
    }

    public void setUpdateTime(LocalDateTime updateTime) {
        this.updateTime = updateTime;
    }

    public List<ReagentPurchaseApplyItem> getItems() {
        return items;
    }

    public void setItems(List<ReagentPurchaseApplyItem> items) {
        this.items = items;
    }

}
