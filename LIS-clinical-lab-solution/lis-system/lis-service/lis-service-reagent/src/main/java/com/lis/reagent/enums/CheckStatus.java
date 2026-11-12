package com.lis.reagent.enums;

/**
 * 盘点单状态，对应 {@code lis_reagent_stock_check.status}。
 *
 * <p>取值口径与实体 {@code ReagentStockCheck#status} 注释一致：
 * 0 进行中、1 已完成、2 已作废。</p>
 *
 * <p>「已完成」是<b>调账已落库</b>的标志位，提交盘点前必须校验当前状态为
 * {@link #IN_PROGRESS}，否则重复提交会对同一批次二次调账（缺陷 P）。</p>
 *
 * @author LIS Team
 */
public enum CheckStatus {

    /** 0 进行中：已生成明细，等待录入实盘数 */
    IN_PROGRESS(0, "进行中"),
    /** 1 已完成：实盘数已录入且盘盈盘亏已调账 */
    FINISHED(1, "已完成"),
    /** 2 已作废：不参与调账 */
    VOIDED(2, "已作废");

    private final int code;
    private final String label;

    CheckStatus(int code, String label) {
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
     * 是否为终态（不可再流转）。
     *
     * @return true 表示已完成或已作废
     */
    public boolean isTerminal() {
        return this == FINISHED || this == VOIDED;
    }

    /**
     * 按状态码解析。
     *
     * @param code 状态码，可为 null
     * @return 匹配的枚举；无匹配返回 null
     */
    public static CheckStatus of(Integer code) {
        if (code == null) {
            return null;
        }
        for (CheckStatus s : values()) {
            if (s.code == code) {
                return s;
            }
        }
        return null;
    }
}
