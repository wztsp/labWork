package com.lis.reagent.enums;

/**
 * 采购申请单状态。
 *
 * <p>状态流转由 {@code com.lis.reagent.support.PurchaseApplyStateMachine} 统一约束，
 * 业务代码禁止散落魔法数字（共享知识第 14 条）。</p>
 *
 * <pre>
 * 0 草稿 ──submit──▶ [预算判定] ──超预算──▶ 5 已冻结 ──unfreeze──▶ 回到 [预算判定]
 *                        └──通过/预警──▶ 1 待审批 ──approve(非末节点)──▶ 2 审批中
 *                                                └──approve(末节点)──▶ 3 已批准
 *                                                └──reject──▶ 4 已驳回 ──resubmit──▶ [预算判定]
 * 任意非终态 ──cancel──▶ 6 已作废
 * </pre>
 */
public enum ApplyStatus {

    /** 0 草稿：可编辑明细 */
    DRAFT(0, "草稿"),
    /** 1 待审批：已进入第一审批节点 */
    PENDING(1, "待审批"),
    /** 2 审批中：已过第一节点，尚未到末节点 */
    APPROVING(2, "审批中"),
    /** 3 已批准：终态，可生成采购订单 */
    APPROVED(3, "已批准"),
    /** 4 已驳回：可修改明细后重新提交 */
    REJECTED(4, "已驳回"),
    /** 5 已冻结：超预算被拦截，需预算调增后解冻 */
    FROZEN(5, "已冻结"),
    /** 6 已作废：终态，占用已释放 */
    CANCELLED(6, "已作废");

    private final int code;
    private final String label;

    ApplyStatus(int code, String label) {
        this.code = code;
        this.label = label;
    }

    public int getCode() {
        return code;
    }

    public String getLabel() {
        return label;
    }

    /**
     * 是否为终态（终态不可再流转）。
     *
     * @return true 表示 已批准 / 已作废
     */
    public boolean isTerminal() {
        return this == APPROVED || this == CANCELLED;
    }

    /**
     * 按状态码解析。
     *
     * @param code 状态码，可为 null
     * @return 匹配的枚举；无匹配返回 null
     */
    public static ApplyStatus of(Integer code) {
        if (code == null) {
            return null;
        }
        for (ApplyStatus s : values()) {
            if (s.code == code) {
                return s;
            }
        }
        return null;
    }
}
