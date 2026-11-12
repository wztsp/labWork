package com.lis.reagent.enums;

/**
 * 审批动作。
 *
 * <p>其中 {@link #APPROVE} / {@link #REJECT} 会落 {@code lis_reagent_purchase_approval}
 * 流水表（action 列取 {@link #getRecordCode()}），其余动作不落审批流水。</p>
 */
public enum ApprovalAction {

    /** 提交（草稿/已驳回 → 预算判定） */
    SUBMIT(0, "提交"),
    /** 审批通过 */
    APPROVE(1, "通过"),
    /** 审批驳回 */
    REJECT(2, "驳回"),
    /** 作废 */
    CANCEL(3, "作废"),
    /** 解冻（已冻结 → 重新走预算判定） */
    UNFREEZE(4, "解冻"),
    /** 重新提交（已驳回 → 预算判定，节点归零重走） */
    RESUBMIT(5, "重新提交");

    private final int recordCode;
    private final String label;

    ApprovalAction(int recordCode, String label) {
        this.recordCode = recordCode;
        this.label = label;
    }

    /**
     * 落审批流水表 {@code lis_reagent_purchase_approval.action} 的取值。
     * 仅 {@link #APPROVE}=1 / {@link #REJECT}=2 会真正落库。
     */
    public int getRecordCode() {
        return recordCode;
    }

    public String getLabel() {
        return label;
    }

    /**
     * 该动作是否需要写审批流水。
     *
     * @return true 表示需要 insert 一条 ReagentPurchaseApproval
     */
    public boolean needAuditTrail() {
        return this == APPROVE || this == REJECT;
    }

    /**
     * 按流水码解析（仅 1/2 有意义）。
     *
     * @param recordCode 流水码，可为 null
     * @return 匹配的枚举；无匹配返回 null
     */
    public static ApprovalAction of(Integer recordCode) {
        if (recordCode == null) {
            return null;
        }
        for (ApprovalAction a : values()) {
            if (a.recordCode == recordCode) {
                return a;
            }
        }
        return null;
    }
}
