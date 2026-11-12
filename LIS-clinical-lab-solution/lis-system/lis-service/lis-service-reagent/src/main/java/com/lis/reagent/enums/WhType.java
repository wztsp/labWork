package com.lis.reagent.enums;

/**
 * 仓库节点类型，对应 {@code lis_reagent_warehouse.wh_type}。
 *
 * <p>三级树：库房 → 冰箱 → 层架。</p>
 */
public enum WhType {

    /** 1 库房（根级） */
    STORE(1, "库房", 1),
    /** 2 冰箱（二级） */
    FRIDGE(2, "冰箱", 2),
    /** 3 层架（三级） */
    SHELF(3, "层架", 3);

    private final int code;
    private final String label;
    private final int expectedLevel;

    WhType(int code, String label, int expectedLevel) {
        this.code = code;
        this.label = label;
        this.expectedLevel = expectedLevel;
    }

    public int getCode() {
        return code;
    }

    public String getLabel() {
        return label;
    }

    /** 该类型在三级树中的期望层级（1/2/3）。 */
    public int getExpectedLevel() {
        return expectedLevel;
    }

    /**
     * 按类型码解析。
     *
     * @param code 类型码，可为 null
     * @return 匹配的枚举；无匹配返回 null
     */
    public static WhType of(Integer code) {
        if (code == null) {
            return null;
        }
        for (WhType t : values()) {
            if (t.code == code) {
                return t;
            }
        }
        return null;
    }
}
