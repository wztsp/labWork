package com.lis.reagent.enums;

/**
 * 供应商资质状态，对应 {@code lis_supplier.qual_status}。
 *
 * <p>由每日 Job 依据 {@code license_expiry} / {@code medical_cert_expiry} 刷新；
 * 仅 {@link #VALID} 的供应商可在采购订单下拉中被选中（R-04 验收 3）。</p>
 */
public enum QualStatus {

    /** 0 待完善：证照信息缺失（存量字符串迁移生成的草稿默认此值） */
    PENDING(0, "待完善"),
    /** 1 有效：两证齐全且均未过期 */
    VALID(1, "有效"),
    /** 2 已过期：任一证照已过期 */
    EXPIRED(2, "已过期");

    private final int code;
    private final String label;

    QualStatus(int code, String label) {
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
     * 是否允许被选为采购订单供应商。
     *
     * @return 仅 {@link #VALID} 返回 true
     */
    public boolean isSelectable() {
        return this == VALID;
    }

    /**
     * 按状态码解析。
     *
     * @param code 状态码，可为 null
     * @return 匹配的枚举；无匹配返回 null
     */
    public static QualStatus of(Integer code) {
        if (code == null) {
            return null;
        }
        for (QualStatus s : values()) {
            if (s.code == code) {
                return s;
            }
        }
        return null;
    }
}
