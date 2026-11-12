package com.lis.reagent.enums;

/**
 * 预算变动类型，对应 {@code lis_reagent_budget_record.change_type}。
 *
 * <p>金额列 {@code amount} 恒为正，增减方向由本枚举决定。</p>
 */
public enum ChangeType {

    /** 1 占用：申请提交时 frozen += amount */
    FREEZE(1, "占用"),
    /** 2 释放：驳回/作废时 frozen -= amount */
    RELEASE(2, "释放"),
    /** 3 实扣：入库验收时 frozen -= 占用额，used += 实付额 */
    SETTLE(3, "实扣"),
    /** 4 调增：财务调增预算总额 */
    INCREASE(4, "调增"),
    /** 5 调减：财务调减预算总额 */
    DECREASE(5, "调减");

    private final int code;
    private final String label;

    ChangeType(int code, String label) {
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
     * 按变动码解析。
     *
     * @param code 变动码，可为 null
     * @return 匹配的枚举；无匹配返回 null
     */
    public static ChangeType of(Integer code) {
        if (code == null) {
            return null;
        }
        for (ChangeType t : values()) {
            if (t.code == code) {
                return t;
            }
        }
        return null;
    }
}
