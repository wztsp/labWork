package com.lis.reagent.enums;

/**
 * 库存批次状态，对应 {@code lis_reagent_stock.status}。
 *
 * <p>取值口径与实体 {@code ReagentStock#status} 注释一致：
 * 0 过期锁定、1 可用、2 已耗尽。</p>
 *
 * <p>状态流转约束：{@link #EXPIRED_LOCKED} 由效期任务写入，业务调账
 * <b>不得</b>擅自把过期锁定的批次改回可用；{@link #EXHAUSTED} 与
 * {@link #AVAILABLE} 之间可随数量增减双向流转。</p>
 *
 * @author LIS Team
 */
public enum StockStatus {

    /** 0 过期锁定：已过效期，禁止出库，仅效期任务可写入 */
    EXPIRED_LOCKED(0, "过期锁定"),
    /** 1 可用：数量大于 0 且未过期 */
    AVAILABLE(1, "可用"),
    /** 2 已耗尽：数量为 0 */
    EXHAUSTED(2, "已耗尽");

    private final int code;
    private final String label;

    StockStatus(int code, String label) {
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
     * 按状态码解析。
     *
     * @param code 状态码，可为 null
     * @return 匹配的枚举；无匹配返回 null
     */
    public static StockStatus of(Integer code) {
        if (code == null) {
            return null;
        }
        for (StockStatus s : values()) {
            if (s.code == code) {
                return s;
            }
        }
        return null;
    }
}
