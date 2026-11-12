package com.lis.reagent.vo;

import com.lis.reagent.entity.ReagentPurchaseApply;
import com.lis.reagent.entity.ReagentPurchaseApplyItem;
import com.lis.reagent.entity.ReagentPurchaseApproval;

import java.io.Serializable;
import java.math.BigDecimal;
import java.util.List;

/**
 * 采购申请详情（主单 + 明细 + 审批流水 + 预算快照 + 可执行动作）。
 *
 * <p>一次返回前端渲染详情页所需的全部数据，避免页面发起 4 次请求。</p>
 *
 * @author LIS Team
 */
public class ApplyDetailVO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 申请主单 */
    private ReagentPurchaseApply apply;
    /** 申请明细列表 */
    private List<ReagentPurchaseApplyItem> items;
    /** 审批流水列表（只增不改不删，按时间升序） */
    private List<ReagentPurchaseApproval> approvals;
    /** 当前登录人可执行的动作码列表，供前端按钮置灰 */
    private List<String> allowedActions;
    /** 当前待审批节点名称 */
    private String currentNodeName;
    /** 命中的预算总额快照 */
    private BigDecimal budgetTotalAmount;
    /** 命中的预算已占用金额快照 */
    private BigDecimal budgetFrozenAmount;
    /** 命中的预算已实扣金额快照 */
    private BigDecimal budgetUsedAmount;
    /** 命中的预算剩余可用额度 */
    private BigDecimal budgetAvailable;
    /** 明细是否可编辑（仅草稿 / 已驳回为 true） */
    private Boolean editable;

    public ApplyDetailVO() {
    }

    public ReagentPurchaseApply getApply() {
        return apply;
    }

    public void setApply(ReagentPurchaseApply apply) {
        this.apply = apply;
    }

    public List<ReagentPurchaseApplyItem> getItems() {
        return items;
    }

    public void setItems(List<ReagentPurchaseApplyItem> items) {
        this.items = items;
    }

    public List<ReagentPurchaseApproval> getApprovals() {
        return approvals;
    }

    public void setApprovals(List<ReagentPurchaseApproval> approvals) {
        this.approvals = approvals;
    }

    public List<String> getAllowedActions() {
        return allowedActions;
    }

    public void setAllowedActions(List<String> allowedActions) {
        this.allowedActions = allowedActions;
    }

    public String getCurrentNodeName() {
        return currentNodeName;
    }

    public void setCurrentNodeName(String currentNodeName) {
        this.currentNodeName = currentNodeName;
    }

    public BigDecimal getBudgetTotalAmount() {
        return budgetTotalAmount;
    }

    public void setBudgetTotalAmount(BigDecimal budgetTotalAmount) {
        this.budgetTotalAmount = budgetTotalAmount;
    }

    public BigDecimal getBudgetFrozenAmount() {
        return budgetFrozenAmount;
    }

    public void setBudgetFrozenAmount(BigDecimal budgetFrozenAmount) {
        this.budgetFrozenAmount = budgetFrozenAmount;
    }

    public BigDecimal getBudgetUsedAmount() {
        return budgetUsedAmount;
    }

    public void setBudgetUsedAmount(BigDecimal budgetUsedAmount) {
        this.budgetUsedAmount = budgetUsedAmount;
    }

    public BigDecimal getBudgetAvailable() {
        return budgetAvailable;
    }

    public void setBudgetAvailable(BigDecimal budgetAvailable) {
        this.budgetAvailable = budgetAvailable;
    }

    public Boolean getEditable() {
        return editable;
    }

    public void setEditable(Boolean editable) {
        this.editable = editable;
    }
}
