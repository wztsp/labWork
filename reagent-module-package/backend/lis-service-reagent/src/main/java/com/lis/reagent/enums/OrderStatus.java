package com.lis.reagent.enums;

/**
 * 采购订单状态。
 *
 * <p>状态码与 DDL {@code lis_reagent_purchase_order.status} 保持一致：
 * 0 待下单 / 1 已下单 / 2 部分到货 / 3 已到货 / 4 已取消。
 * 业务代码禁止散落魔法数字（共享知识第 14 条），统一以本枚举常量表达状态。</p>
 *
 * <pre>
 * 0 待下单 ──place──▶ 1 已下单 ──部分到货──▶ 2 部分到货
 *                                  └──全部到货──▶ 3 已到货（终态）
 * 任意态 ──cancel──▶ 4 已取消（终态）
 * </pre>
 */
public enum OrderStatus {

    /** 0 待下单：订单已建，尚未下达供应商 */
    PENDING_ORDER(0, "待下单"),
    /** 1 已下单：已下达供应商 */
    PLACED(1, "已下单"),
    /** 2 部分到货：到货验收进行中 */
    PARTIAL_ARRIVED(2, "部分到货"),
    /** 3 已到货：全部到货，终态 */
    ARRIVED(3, "已到货"),
    /** 4 已取消：终态，占用已释放 */
    CANCELLED(4, "已取消");

    private final int code;
    private final String label;

    OrderStatus(int code, String label) {
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
     * @return true 表示 {@link #ARRIVED} 或 {@link #CANCELLED}
     */
    public boolean isTerminal() {
        return this == ARRIVED || this == CANCELLED;
    }

    /**
     * 按状态码解析。
     *
     * @param code 状态码，可为 null
     * @return 匹配的枚举；无匹配返回 null
     */
    public static OrderStatus of(Integer code) {
        if (code == null) {
            return null;
        }
        for (OrderStatus s : values()) {
            if (s.code == code) {
                return s;
            }
        }
        return null;
    }
}
