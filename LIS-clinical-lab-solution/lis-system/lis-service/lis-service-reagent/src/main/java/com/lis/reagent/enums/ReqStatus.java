package com.lis.reagent.enums;

/**
 * 试剂业务单（领用 / 退回 / 报废 / 调拨 / 借用 / 归还）状态。
 *
 * <p>状态码与 DDL {@code lis_reagent_requisition.status} 保持一致：
 * 0 草稿 / 1 待审 / 2 已审 / 3 已执行 / 4 已驳回 / 5 已作废。
 * 六类单据共用一张表，状态机统一以本枚举表达，禁止散落魔法数字（共享知识第 14 条）。</p>
 *
 * <pre>
 * 0 草稿 ──submit──▶ 1 待审 ──approve──▶ 2 已审 ──execute──▶ 3 已执行（终态）
 *                        └──reject──▶ 4 已驳回（终态）
 * 任意非终态 ──void──▶ 5 已作废（终态）
 * </pre>
 */
public enum ReqStatus {

    /** 0 草稿：可编辑明细 */
    DRAFT(0, "草稿"),
    /** 1 待审：已提交待审批 */
    PENDING(1, "待审"),
    /** 2 已审：审批通过，待执行 */
    APPROVED(2, "已审"),
    /** 3 已执行：终态，已实际出入库 */
    EXECUTED(3, "已执行"),
    /** 4 已驳回：终态，可修改后重提 */
    REJECTED(4, "已驳回"),
    /** 5 已作废：终态，占用已释放 */
    CANCELLED(5, "已作废");

    private final int code;
    private final String label;

    ReqStatus(int code, String label) {
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
     * @return true 表示 {@link #EXECUTED} / {@link #REJECTED} / {@link #CANCELLED}
     */
    public boolean isTerminal() {
        return this == EXECUTED || this == REJECTED || this == CANCELLED;
    }

    /**
     * 按状态码解析。
     *
     * @param code 状态码，可为 null
     * @return 匹配的枚举；无匹配返回 null
     */
    public static ReqStatus of(Integer code) {
        if (code == null) {
            return null;
        }
        for (ReqStatus s : values()) {
            if (s.code == code) {
                return s;
            }
        }
        return null;
    }
}
