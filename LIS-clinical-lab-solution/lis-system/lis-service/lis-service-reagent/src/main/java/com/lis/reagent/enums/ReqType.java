package com.lis.reagent.enums;

/**
 * 统一出入库业务单类型，对应 {@code lis_reagent_requisition.req_type}。
 *
 * <p>六类合一，业务分派统一走 {@code com.lis.reagent.support.RequisitionTypeSpec}，
 * 禁止在 Service 中写 {@code switch(reqType)}（共享知识第 10 条）。</p>
 */
public enum ReqType {

    /** 1 领用（出库） */
    PICK(1, "领用"),
    /** 2 退库（入库） */
    RETURN(2, "退库"),
    /** 3 报废（出库，reason 必填） */
    SCRAP(3, "报废"),
    /** 4 调拨（移库，第二批 R-08） */
    TRANSFER(4, "调拨"),
    /** 5 借用（出库，第二批 R-08） */
    BORROW(5, "借用"),
    /** 6 归还（入库，第二批 R-08） */
    GIVEBACK(6, "归还"),
    /** 7 退货出库（出库，退回供应商） */
    RETURN_SUPPLIER(7, "退货出库"),
    /** 8 作废出库（出库，过期/失效试剂作废） */
    OBSOLETE(8, "作废出库");

    private final int code;
    private final String label;

    ReqType(int code, String label) {
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
     * 按类型码解析。
     *
     * @param code 类型码，可为 null
     * @return 匹配的枚举；无匹配返回 null
     */
    public static ReqType of(Integer code) {
        if (code == null) {
            return null;
        }
        for (ReqType t : values()) {
            if (t.code == code) {
                return t;
            }
        }
        return null;
    }
}
