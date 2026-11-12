package com.lis.reagent.support;

import com.lis.common.core.exception.BusinessException;
import com.lis.reagent.enums.Direction;

/**
 * 出入库业务单类型规约（零 Spring 依赖的枚举，可脱离容器直接单测）。
 *
 * <p><b>反模式（禁止）</b>：在 {@code ReagentRequisitionServiceImpl.execute()} 里写
 * {@code switch (reqType) { case 1: ... case 6: ... }} 六个分支 —— 每加一种类型就要改核心方法，
 * 且六段代码各自演化后互相打架。</p>
 *
 * <p><b>本方案</b>：把 6 类单据的差异全部收敛到本枚举，执行器只按 {@link #getDirection()}
 * 做 IN / OUT / MOVE 三分支分派，分支数永远不增加（共享知识第 10 条）。</p>
 *
 * @author LIS Team
 */
public enum RequisitionTypeSpec {

    //        码 方向             流水类型 需仓库对 默认审批 批次必填  名称
    /** 领用出库 */
    PICK(1, Direction.OUT, 2, false, false, true, "领用"),
    /** 退库入库 */
    RETURN(2, Direction.IN, 4, false, false, true, "退库"),
    /** 报废出库，必须填写报废原因 */
    SCRAP(3, Direction.OUT, 3, false, true, true, "报废"),
    /** 调拨移库（第二批 R-13），源仓出、目标仓入，同事务两条流水 */
    TRANSFER(4, Direction.MOVE, 7, true, true, true, "调拨"),
    /** 借用出库（第二批 R-13），须填预计归还日 */
    BORROW(5, Direction.OUT, 8, false, true, true, "借用"),
    /** 归还入库（第二批 R-13），须关联原借用单 */
    GIVEBACK(6, Direction.IN, 9, false, false, true, "归还"),
    /** 退货出库：退回供应商，按批次扣减库存 */
    RETURN_SUPPLIER(7, Direction.OUT, 10, false, false, true, "退货出库"),
    /** 作废出库：过期/失效试剂作废，按批次扣减库存 */
    OBSOLETE(8, Direction.OUT, 11, false, false, true, "作废出库");

    private final int code;
    private final Direction direction;
    private final int recordType;
    private final boolean warehousePairRequired;
    private final boolean defaultApproval;
    private final boolean lotRequired;
    private final String label;

    RequisitionTypeSpec(int code, Direction direction, int recordType,
                        boolean warehousePairRequired, boolean defaultApproval,
                        boolean lotRequired, String label) {
        this.code = code;
        this.direction = direction;
        this.recordType = recordType;
        this.warehousePairRequired = warehousePairRequired;
        this.defaultApproval = defaultApproval;
        this.lotRequired = lotRequired;
        this.label = label;
    }

    /** 单据类型码，落库到 {@code lis_reagent_requisition.req_type} */
    public int getCode() {
        return code;
    }

    /**
     * 库存变动方向。执行器按此做三分支分派。
     *
     * @return IN 入库 / OUT 出库 / MOVE 移库
     */
    public Direction getDirection() {
        return direction;
    }

    /**
     * 对应的库存流水类型，落库到 {@code lis_reagent_stock_record.record_type}。
     *
     * @return 流水类型码
     */
    public int getRecordType() {
        return recordType;
    }

    /**
     * 数量符号：入库 +1、出库 -1、移库 0（移库需成对写两条流水，符号在各自分支内确定）。
     *
     * @return 符号值
     */
    public int getQtySign() {
        return direction.getSign();
    }

    /** 是否必须同时指定源仓与目标仓（仅调拨为 true） */
    public boolean isWarehousePairRequired() {
        return warehousePairRequired;
    }

    /** 是否必须指定批次（stockId），六类单据目前均为 true */
    public boolean isLotRequired() {
        return lotRequired;
    }

    public String getLabel() {
        return label;
    }

    /**
     * 是否需要走审批。
     *
     * <p>三个来源取「或」：类型自带默认审批要求、单据含危险品（主理人 Q6 强制审批）、
     * 系统全局开关。</p>
     *
     * @param hazard       本单据是否含危险品
     * @param globalSwitch 全局审批开关（来自 sys_config）
     * @return true 表示必须审批
     */
    public boolean requiresApproval(boolean hazard, boolean globalSwitch) {
        return this.defaultApproval || hazard || globalSwitch;
    }

    /** 是否必须填写报废原因（仅报废单） */
    public boolean requiresScrapReason() {
        return this == SCRAP;
    }

    /** 是否必须填写预计归还日期（仅借用单） */
    public boolean requiresExpectReturnDate() {
        return this == BORROW;
    }

    /** 是否必须关联原借用单（仅归还单） */
    public boolean requiresSourceReq() {
        return this == GIVEBACK;
    }

    /**
     * 是否为第二批（R-13）才启用的类型。第一批 Service 可据此拒绝受理。
     *
     * @return true 表示调拨 / 借用 / 归还
     */
    public boolean isSecondPhase() {
        return this == TRANSFER || this == BORROW || this == GIVEBACK;
    }

    /**
     * 按类型码解析。
     *
     * @param reqType 单据类型码，不能为 null
     * @return 匹配的规约，恒不为 null
     * @throws BusinessException 类型码为 null 或未知时抛出
     */
    public static RequisitionTypeSpec of(Integer reqType) {
        if (reqType == null) {
            throw new BusinessException("单据类型不能为空");
        }
        for (RequisitionTypeSpec spec : values()) {
            if (spec.code == reqType) {
                return spec;
            }
        }
        throw new BusinessException("未知的单据类型：" + reqType);
    }
}
