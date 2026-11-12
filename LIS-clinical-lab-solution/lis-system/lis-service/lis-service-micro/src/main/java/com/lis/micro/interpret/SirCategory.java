package com.lis.micro.interpret;

/**
 * 药敏判读类别 (CLSI / EUCAST 通用)
 */
public enum SirCategory {

    /** 敏感 Susceptible */
    S("S", "敏感"),

    /** 中介 Intermediate */
    I("I", "中介"),

    /** 剂量依赖敏感 Susceptible-Dose Dependent (枚举预留, 一期不产出) */
    SDD("SDD", "剂量依赖敏感"),

    /** 耐药 Resistant */
    R("R", "耐药"),

    /** 无法判读 / 降级 Not Interpretable */
    NI("NI", "无法判读");

    private final String code;
    private final String label;

    SirCategory(String code, String label) {
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
     * 按代码解析类别, 未知代码统一返回 {@link #NI}, 绝不抛异常。
     *
     * @param code 类别代码, 大小写不敏感, 允许前后空白
     * @return 对应类别, 无法识别时为 NI
     */
    public static SirCategory fromCode(String code) {
        if (code == null) {
            return NI;
        }
        String c = code.trim().toUpperCase();
        if (c.isEmpty()) {
            return NI;
        }
        for (SirCategory category : values()) {
            if (category.code.equals(c)) {
                return category;
            }
        }
        return NI;
    }
}
