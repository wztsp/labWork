package com.lis.reagent.enums;

/**
 * 审批业务域，对应 {@code lis_reagent_approval_node.biz_type}（VARCHAR）。
 */
public enum BizType {

    /** 采购申请审批链 */
    PURCHASE("PURCHASE", "采购申请"),
    /** 出入库业务单审批链 */
    REQUISITION("REQUISITION", "出入库业务单");

    private final String code;
    private final String label;

    BizType(String code, String label) {
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
     * 按业务码解析（大小写不敏感）。
     *
     * @param code 业务码，可为 null
     * @return 匹配的枚举；无匹配返回 null
     */
    public static BizType of(String code) {
        if (code == null || code.trim().isEmpty()) {
            return null;
        }
        String key = code.trim();
        for (BizType t : values()) {
            if (t.code.equalsIgnoreCase(key)) {
                return t;
            }
        }
        return null;
    }
}
