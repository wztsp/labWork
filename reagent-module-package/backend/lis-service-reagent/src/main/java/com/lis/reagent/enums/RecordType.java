package com.lis.reagent.enums;

/**
 * 库存流水类型，对应 {@code lis_reagent_stock_record.record_type}。
 *
 * <p>取值口径与 PRD §6.3 对照表一致（共享知识第 14 条）：
 * 1入库 2出库(领用) 3报损/报废 4退库 5盘点调账 6消耗 7调拨 8借用 9归还。</p>
 */
public enum RecordType {

    /** 1 入库 */
    IN(1, "入库"),
    /** 2 出库（领用） */
    OUT_PICK(2, "出库(领用)"),
    /** 3 报损 / 报废 */
    SCRAP(3, "报损/报废"),
    /** 4 退库 */
    RETURN(4, "退库"),
    /** 5 盘点调账 */
    CHECK_ADJUST(5, "盘点调账"),
    /** 6 消耗（检验/质控自动扣减） */
    CONSUME(6, "消耗"),
    /** 7 调拨 */
    TRANSFER(7, "调拨"),
    /** 8 借用 */
    BORROW(8, "借用"),
    /** 9 归还 */
    GIVEBACK(9, "归还");

    private final int code;
    private final String label;

    RecordType(int code, String label) {
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
     * 按流水类型码解析。
     *
     * @param code 流水类型码，可为 null
     * @return 匹配的枚举；无匹配返回 null
     */
    public static RecordType of(Integer code) {
        if (code == null) {
            return null;
        }
        for (RecordType t : values()) {
            if (t.code == code) {
                return t;
            }
        }
        return null;
    }
}
