package com.lis.reagent.enums;

/**
 * 库存变动方向。
 *
 * <p>由 {@code com.lis.reagent.support.RequisitionTypeSpec#getDirection()} 给出，
 * {@code ReagentRequisitionServiceImpl.execute()} 按本枚举做三分支分派，
 * 新增单据类型只需扩枚举，不改 switch（共享知识第 10 条）。</p>
 */
public enum Direction {

    /** 入库方向：库存数量增加（退库 / 归还） */
    IN("IN", "入库", 1),
    /** 出库方向：库存数量减少（领用 / 报废 / 借用） */
    OUT("OUT", "出库", -1),
    /** 移库方向：总量不变，源仓减、目标仓增（调拨） */
    MOVE("MOVE", "移库", 0);

    private final String code;
    private final String label;
    private final int sign;

    Direction(String code, String label, int sign) {
        this.code = code;
        this.label = label;
        this.sign = sign;
    }

    public String getCode() {
        return code;
    }

    public String getLabel() {
        return label;
    }

    /**
     * 数量符号：入库 +1，出库 -1，移库 0（移库需成对写两条流水）。
     *
     * @return 符号值
     */
    public int getSign() {
        return sign;
    }

    /**
     * 按方向码解析（大小写不敏感）。
     *
     * @param code 方向码，可为 null
     * @return 匹配的枚举；无匹配返回 null
     */
    public static Direction of(String code) {
        if (code == null || code.trim().isEmpty()) {
            return null;
        }
        String key = code.trim();
        for (Direction d : values()) {
            if (d.code.equalsIgnoreCase(key)) {
                return d;
            }
        }
        return null;
    }
}
