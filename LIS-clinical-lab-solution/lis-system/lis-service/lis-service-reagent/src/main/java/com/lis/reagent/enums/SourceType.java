package com.lis.reagent.enums;

/**
 * 预算流水来源单据类型，对应 {@code lis_reagent_budget_record.source_type}（VARCHAR）。
 *
 * <p>与 {@code (budget_id, change_type, source_type, source_id)} 唯一索引配合实现幂等。</p>
 */
public enum SourceType {

    /** 采购申请单 */
    APPLY("APPLY", "采购申请"),
    /** 采购订单 */
    ORDER("ORDER", "采购订单"),
    /** 入库单（验收实扣） */
    INBOUND("INBOUND", "入库单"),
    /** 人工调整（财务调增/调减） */
    MANUAL("MANUAL", "人工调整");

    private final String code;
    private final String label;

    SourceType(String code, String label) {
        this.code = code;
        this.label = label;
    }

    public String getCode() {
        return code;
    }

    public String getLabel() {
        return label;
    }

    /**
     * 按来源码解析（大小写不敏感）。
     *
     * @param code 来源码，可为 null
     * @return 匹配的枚举；无匹配返回 null
     */
    public static SourceType of(String code) {
        if (code == null || code.trim().isEmpty()) {
            return null;
        }
        String key = code.trim();
        for (SourceType t : values()) {
            if (t.code.equalsIgnoreCase(key)) {
                return t;
            }
        }
        return null;
    }
}
