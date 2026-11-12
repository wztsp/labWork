package com.lis.reagent.dto;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * 采购订单创建入参。
 *
 * <p>订单由已批准的采购申请转化而来（{@code applyId} 必填），
 * 明细可对申请明细做裁剪与拆分供应商，但数量总和不得超过申请数量。</p>
 *
 * @author LIS Team
 */
public class OrderCreateDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 订单 ID，新建时为 null */
    private Long id;
    /** 来源采购申请 ID，必填 */
    private Long applyId;
    /** 供应商 ID，必填 */
    private Long supplierId;
    /** 供应商名称快照 */
    private String supplierName;
    /** 归属科室 ID */
    private Long deptId;
    /** 归属科室名称 */
    private String deptName;
    /** 期望到货日期 */
    private LocalDate expectDate;
    /** 下单日期；为空取当天 */
    private LocalDate orderDate;
    /** 操作人 ID */
    private Long operatorId;
    /** 操作人姓名 */
    private String operatorName;
    /** 备注 */
    private String remark;
    /** 明细列表，至少一条 */
    private List<OrderItemDTO> items;

    public OrderCreateDTO() {
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getApplyId() {
        return applyId;
    }

    public void setApplyId(Long applyId) {
        this.applyId = applyId;
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

    public LocalDate getExpectDate() {
        return expectDate;
    }

    public void setExpectDate(LocalDate expectDate) {
        this.expectDate = expectDate;
    }

    public LocalDate getOrderDate() {
        return orderDate;
    }

    public void setOrderDate(LocalDate orderDate) {
        this.orderDate = orderDate;
    }

    public Long getOperatorId() {
        return operatorId;
    }

    public void setOperatorId(Long operatorId) {
        this.operatorId = operatorId;
    }

    public String getOperatorName() {
        return operatorName;
    }

    public void setOperatorName(String operatorName) {
        this.operatorName = operatorName;
    }

    public String getRemark() {
        return remark;
    }

    public void setRemark(String remark) {
        this.remark = remark;
    }

    public List<OrderItemDTO> getItems() {
        return items;
    }

    public void setItems(List<OrderItemDTO> items) {
        this.items = items;
    }

    /**
     * 采购订单明细入参。
     */
    public static class OrderItemDTO implements Serializable {

        private static final long serialVersionUID = 1L;

        /** 明细 ID，新建时为 null */
        private Long id;
        /** 来源申请明细 ID，必填（用于 arrivedQty 精确回写，见 C-4） */
        private Long applyItemId;
        /** 试剂 ID，必填 */
        private Long reagentId;
        /** 订购数量，必填且必须大于 0 */
        private BigDecimal quantity;
        /** 单位 */
        private String unit;
        /** 成交单价，必填 */
        private BigDecimal unitPrice;
        /** 备注 */
        private String remark;

        public OrderItemDTO() {
        }

        public Long getId() {
            return id;
        }

        public void setId(Long id) {
            this.id = id;
        }

        public Long getApplyItemId() {
            return applyItemId;
        }

        public void setApplyItemId(Long applyItemId) {
            this.applyItemId = applyItemId;
        }

        public Long getReagentId() {
            return reagentId;
        }

        public void setReagentId(Long reagentId) {
            this.reagentId = reagentId;
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

        public BigDecimal getUnitPrice() {
            return unitPrice;
        }

        public void setUnitPrice(BigDecimal unitPrice) {
            this.unitPrice = unitPrice;
        }

        public String getRemark() {
            return remark;
        }

        public void setRemark(String remark) {
            this.remark = remark;
        }
    }
}
