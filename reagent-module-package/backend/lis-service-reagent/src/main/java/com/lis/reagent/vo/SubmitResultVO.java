package com.lis.reagent.vo;

import java.io.Serializable;
import java.math.BigDecimal;

/**
 * 采购申请提交结果。
 *
 * <p>提交动作有三种结局，必须让前端能区分并给出不同提示（R-03 验收 7）：</p>
 * <ul>
 *   <li>{@code PASS}   —— 正常进入审批流</li>
 *   <li>{@code WARN}   —— 进入审批流，但预算占用率已达预警线，页面需黄色提示</li>
 *   <li>{@code EXCEED} —— 单据被置为「已冻结」，页面需明示超额金额并提示联系财务调增预算</li>
 * </ul>
 *
 * @author LIS Team
 */
public class SubmitResultVO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 申请单 ID */
    private Long applyId;
    /** 申请单号 */
    private String applyNo;
    /** 提交后单据状态码，取 {@code ApplyStatus.getCode()} */
    private Integer status;
    /** 状态中文名 */
    private String statusLabel;
    /** 预算判定结果码：PASS / WARN / EXCEED */
    private String verdict;
    /** 是否超预算被冻结 */
    private Boolean overBudget;
    /** 超额金额；未超额时为 0 */
    private BigDecimal excessAmount;
    /** 命中的预算 ID */
    private Long budgetId;
    /** 本次占用金额 */
    private BigDecimal totalAmount;
    /** 解析出的审批链总节点数；0 表示无需审批 */
    private Integer totalNodeCount;
    /** 当前所处节点序号 */
    private Integer currentNodeLevel;
    /** 当前待审批节点名称 */
    private String currentNodeName;
    /** 给用户的提示文案 */
    private String message;

    public SubmitResultVO() {
    }

    public Long getApplyId() {
        return applyId;
    }

    public void setApplyId(Long applyId) {
        this.applyId = applyId;
    }

    public String getApplyNo() {
        return applyNo;
    }

    public void setApplyNo(String applyNo) {
        this.applyNo = applyNo;
    }

    public Integer getStatus() {
        return status;
    }

    public void setStatus(Integer status) {
        this.status = status;
    }

    public String getStatusLabel() {
        return statusLabel;
    }

    public void setStatusLabel(String statusLabel) {
        this.statusLabel = statusLabel;
    }

    public String getVerdict() {
        return verdict;
    }

    public void setVerdict(String verdict) {
        this.verdict = verdict;
    }

    public Boolean getOverBudget() {
        return overBudget;
    }

    public void setOverBudget(Boolean overBudget) {
        this.overBudget = overBudget;
    }

    public BigDecimal getExcessAmount() {
        return excessAmount;
    }

    public void setExcessAmount(BigDecimal excessAmount) {
        this.excessAmount = excessAmount;
    }

    public Long getBudgetId() {
        return budgetId;
    }

    public void setBudgetId(Long budgetId) {
        this.budgetId = budgetId;
    }

    public BigDecimal getTotalAmount() {
        return totalAmount;
    }

    public void setTotalAmount(BigDecimal totalAmount) {
        this.totalAmount = totalAmount;
    }

    public Integer getTotalNodeCount() {
        return totalNodeCount;
    }

    public void setTotalNodeCount(Integer totalNodeCount) {
        this.totalNodeCount = totalNodeCount;
    }

    public Integer getCurrentNodeLevel() {
        return currentNodeLevel;
    }

    public void setCurrentNodeLevel(Integer currentNodeLevel) {
        this.currentNodeLevel = currentNodeLevel;
    }

    public String getCurrentNodeName() {
        return currentNodeName;
    }

    public void setCurrentNodeName(String currentNodeName) {
        this.currentNodeName = currentNodeName;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }
}
