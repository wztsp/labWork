package com.lis.reagent.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 供应商发票。
 *
 * <p>对应表 {@code lis_reagent_invoice}。发票号由数据库唯一索引
 * {@code uk_lis_reagent_invoice_no} 兜底，作废走 {@code status = 0} 逻辑作废。</p>
 *
 * <p>手写 getter/setter，不使用 Lombok（与既有实体保持一致）。</p>
 */
@TableName("lis_reagent_invoice")
public class ReagentInvoice implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 主键 */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 发票号，唯一 */
    private String invoiceNo;

    /** 供应商主数据 ID，关联 lis_supplier.id */
    private Long supplierId;

    /** 供应商名称快照 */
    private String supplierName;

    /** 开票日期 */
    private LocalDate invoiceDate;

    /** 价税合计 */
    private BigDecimal amount;

    /** 税额 */
    private BigDecimal taxAmount;

    /** 关联采购订单 ID */
    private Long orderId;

    /** 关联采购订单号快照 */
    private String orderNo;

    /** 状态：1已登记 0已作废 */
    private Integer status;

    /** 备注 */
    private String remark;

    /** 登记人姓名 */
    private String operatorName;

    /** 登记时间 */
    private LocalDateTime createTime;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getInvoiceNo() {
        return invoiceNo;
    }

    public void setInvoiceNo(String invoiceNo) {
        this.invoiceNo = invoiceNo;
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

    public LocalDate getInvoiceDate() {
        return invoiceDate;
    }

    public void setInvoiceDate(LocalDate invoiceDate) {
        this.invoiceDate = invoiceDate;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public void setAmount(BigDecimal amount) {
        this.amount = amount;
    }

    public BigDecimal getTaxAmount() {
        return taxAmount;
    }

    public void setTaxAmount(BigDecimal taxAmount) {
        this.taxAmount = taxAmount;
    }

    public Long getOrderId() {
        return orderId;
    }

    public void setOrderId(Long orderId) {
        this.orderId = orderId;
    }

    public String getOrderNo() {
        return orderNo;
    }

    public void setOrderNo(String orderNo) {
        this.orderNo = orderNo;
    }

    public Integer getStatus() {
        return status;
    }

    public void setStatus(Integer status) {
        this.status = status;
    }

    public String getRemark() {
        return remark;
    }

    public void setRemark(String remark) {
        this.remark = remark;
    }

    public String getOperatorName() {
        return operatorName;
    }

    public void setOperatorName(String operatorName) {
        this.operatorName = operatorName;
    }

    public LocalDateTime getCreateTime() {
        return createTime;
    }

    public void setCreateTime(LocalDateTime createTime) {
        this.createTime = createTime;
    }
}
