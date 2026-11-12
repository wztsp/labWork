package com.lis.reagent.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 试剂入库单
 */
@TableName("lis_reagent_inbound")
public class ReagentInbound {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 入库单号 */
    private String inboundNo;
    /** 供应商名称（旧字段，保留冗余快照） */
    private String supplier;
    /** 来源采购订单 ID，关联 lis_reagent_purchase_order.id */
    private Long orderId;
    /** 来源采购订单号快照 */
    private String orderNo;
    /** 供应商主数据 ID，关联 lis_supplier.id */
    private Long supplierId;
    /** 总金额 */
    private BigDecimal totalAmount;
    /** 0待验收 1已验收 2拒收 */
    private Integer verifyStatus;
    private Long verifierId;
    private String verifierName;
    private LocalDateTime verifyTime;
    private String remark;
    private LocalDateTime createTime;

    /** 入库明细列表(非数据库字段) */
    @com.baomidou.mybatisplus.annotation.TableField(exist = false)
    private List<ReagentInboundItem> items;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getInboundNo() {
        return inboundNo;
    }

    public void setInboundNo(String inboundNo) {
        this.inboundNo = inboundNo;
    }

    public String getSupplier() {
        return supplier;
    }

    public void setSupplier(String supplier) {
        this.supplier = supplier;
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

    public Long getSupplierId() {
        return supplierId;
    }

    public void setSupplierId(Long supplierId) {
        this.supplierId = supplierId;
    }

    public BigDecimal getTotalAmount() {
        return totalAmount;
    }

    public void setTotalAmount(BigDecimal totalAmount) {
        this.totalAmount = totalAmount;
    }

    public Integer getVerifyStatus() {
        return verifyStatus;
    }

    public void setVerifyStatus(Integer verifyStatus) {
        this.verifyStatus = verifyStatus;
    }

    public Long getVerifierId() {
        return verifierId;
    }

    public void setVerifierId(Long verifierId) {
        this.verifierId = verifierId;
    }

    public String getVerifierName() {
        return verifierName;
    }

    public void setVerifierName(String verifierName) {
        this.verifierName = verifierName;
    }

    public LocalDateTime getVerifyTime() {
        return verifyTime;
    }

    public void setVerifyTime(LocalDateTime verifyTime) {
        this.verifyTime = verifyTime;
    }

    public String getRemark() {
        return remark;
    }

    public void setRemark(String remark) {
        this.remark = remark;
    }

    public LocalDateTime getCreateTime() {
        return createTime;
    }

    public void setCreateTime(LocalDateTime createTime) {
        this.createTime = createTime;
    }

    public List<ReagentInboundItem> getItems() {
        return items;
    }

    public void setItems(List<ReagentInboundItem> items) {
        this.items = items;
    }

}
