package com.lis.reagent.dto;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * 出入库业务单创建入参（领用 / 退库 / 报废 / 调拨 / 借用 / 归还六类共用）。
 *
 * <p>类型差异由 {@code reqType} 驱动，Service 通过
 * {@code RequisitionTypeSpec.of(reqType)} 取规约后统一校验与分派，
 * 不写 {@code switch(reqType)} 六分支。</p>
 *
 * @author LIS Team
 */
public class RequisitionCreateDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 单据 ID，新建时为 null */
    private Long id;
    /** 单据类型：1领用 2退库 3报废 4调拨 5借用 6归还 7退货出库 8作废出库，必填 */
    private Integer reqType;
    /** 归属科室 ID */
    private Long deptId;
    /** 归属科室名称 */
    private String deptName;
    /** 申请人 ID */
    private Long applyUserId;
    /** 申请人姓名 */
    private String applyUserName;
    /** 源仓库 ID（出库类 / 调拨源） */
    private Long fromWarehouseId;
    /** 目标仓库 ID（入库类 / 调拨目标） */
    private Long toWarehouseId;
    /** 预计归还日期，reqType=5 借用时必填 */
    private LocalDate expectReturnDate;
    /** 原借用单 ID，reqType=6 归还时必填 */
    private Long sourceReqId;
    /** 事由，reqType=3 报废时必填 */
    private String reason;
    /** 退货供应商 ID，reqType=7 退货出库时使用 */
    private Long supplierId;
    /** 退货供应商名称快照 */
    private String supplierName;
    /** 备注 */
    private String remark;
    /** 明细列表，至少一条 */
    private List<RequisitionItemDTO> items;

    public RequisitionCreateDTO() {
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Integer getReqType() {
        return reqType;
    }

    public void setReqType(Integer reqType) {
        this.reqType = reqType;
    }

    public Long getDeptId() {
        return deptId;
    }

    public void setDeptId(Long deptId) {
        this.deptId = deptId;
    }

    public String getDeptName() {
        return deptName;
    }

    public void setDeptName(String deptName) {
        this.deptName = deptName;
    }

    public Long getApplyUserId() {
        return applyUserId;
    }

    public void setApplyUserId(Long applyUserId) {
        this.applyUserId = applyUserId;
    }

    public String getApplyUserName() {
        return applyUserName;
    }

    public void setApplyUserName(String applyUserName) {
        this.applyUserName = applyUserName;
    }

    public Long getFromWarehouseId() {
        return fromWarehouseId;
    }

    public void setFromWarehouseId(Long fromWarehouseId) {
        this.fromWarehouseId = fromWarehouseId;
    }

    public Long getToWarehouseId() {
        return toWarehouseId;
    }

    public void setToWarehouseId(Long toWarehouseId) {
        this.toWarehouseId = toWarehouseId;
    }

    public LocalDate getExpectReturnDate() {
        return expectReturnDate;
    }

    public void setExpectReturnDate(LocalDate expectReturnDate) {
        this.expectReturnDate = expectReturnDate;
    }

    public Long getSourceReqId() {
        return sourceReqId;
    }

    public void setSourceReqId(Long sourceReqId) {
        this.sourceReqId = sourceReqId;
    }

    public String getReason() {
        return reason;
    }

    public void setReason(String reason) {
        this.reason = reason;
    }

    public Long getSupplierId() {
        return supplierId;
    }

    public void setSupplierId(Long supplierId) {
        this.supplierId = supplierId;
    }

    public String getSupplierName() {
        return supplierName;
    }

    public void setSupplierName(String supplierName) {
        this.supplierName = supplierName;
    }

    public String getRemark() {
        return remark;
    }

    public void setRemark(String remark) {
        this.remark = remark;
    }

    public List<RequisitionItemDTO> getItems() {
        return items;
    }

    public void setItems(List<RequisitionItemDTO> items) {
        this.items = items;
    }

    /**
     * 出入库业务单明细入参。
     */
    public static class RequisitionItemDTO implements Serializable {

        private static final long serialVersionUID = 1L;

        /** 明细 ID，新建时为 null */
        private Long id;
        /** 试剂 ID，必填 */
        private Long reagentId;
        /** 指定批次库存 ID；出库类必填 */
        private Long stockId;
        /** 批号；入库类（退库 / 归还）新建批次时使用 */
        private String lotNo;
        /** 有效期；入库类新建批次时使用 */
        private LocalDate expiryDate;
        /** 数量，必填且必须大于 0（方向由 reqType 决定，此处恒为正数） */
        private BigDecimal quantity;
        /** 单位 */
        private String unit;
        /** 备注 */
        private String remark;

        public RequisitionItemDTO() {
        }

        public Long getId() {
            return id;
        }

        public void setId(Long id) {
            this.id = id;
        }

        public Long getReagentId() {
            return reagentId;
        }

        public void setReagentId(Long reagentId) {
            this.reagentId = reagentId;
        }

        public Long getStockId() {
            return stockId;
        }

        public void setStockId(Long stockId) {
            this.stockId = stockId;
        }

        public String getLotNo() {
            return lotNo;
        }

        public void setLotNo(String lotNo) {
            this.lotNo = lotNo;
        }

        public LocalDate getExpiryDate() {
            return expiryDate;
        }

        public void setExpiryDate(LocalDate expiryDate) {
            this.expiryDate = expiryDate;
        }

        public BigDecimal getQuantity() {
            return quantity;
        }

        public void setQuantity(BigDecimal quantity) {
            this.quantity = quantity;
        }

        public String getUnit() {
            return unit;
        }

        public void setUnit(String unit) {
            this.unit = unit;
        }

        public String getRemark() {
            return remark;
        }

        public void setRemark(String remark) {
            this.remark = remark;
        }
    }
}
