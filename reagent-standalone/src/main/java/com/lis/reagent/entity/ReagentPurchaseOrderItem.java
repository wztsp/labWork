package com.lis.reagent.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 试剂采购订单明细。
 *
 * <p>{@code arrivedQty} 由 {@code verifyInbound()} 经
 * {@code ReagentPurchaseOrderItemMapper.receiveQty()} 条件 UPDATE 精确回写；
 * 数据库 CHECK 约束 {@code arrived_qty <= quantity} 作为防超收兜底（R-02 验收 7 双保险）。</p>
 *
 * <p>入库明细通过 {@code lis_reagent_inbound_item.order_item_id} 关联到本行，
 * 解决「同一订单内同一试剂多行时 arrivedQty 无法精确回写」的问题（C-4）。</p>
 *
 * <p>手写 getter/setter，不使用 Lombok（C-10）。不声明 {@code deleted} 字段（C-9 复核结论）。</p>
 */
@TableName("lis_reagent_purchase_order_item")
public class ReagentPurchaseOrderItem {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long orderId;
    /** 来源申请明细 */
    private Long applyItemId;
    private Long reagentId;
    private String reagentCode;
    private String reagentName;
    private String specification;
    /** 订购数量 */
    private BigDecimal quantity;
    /** 已到货累计 */
    private BigDecimal arrivedQty;
    private String unit;
    private BigDecimal unitPrice;
    private BigDecimal amount;
    private String remark;
    private LocalDateTime createTime;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getOrderId() {
        return orderId;
    }

    public void setOrderId(Long orderId) {
        this.orderId = orderId;
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

    public String getReagentCode() {
        return reagentCode;
    }

    public void setReagentCode(String reagentCode) {
        this.reagentCode = reagentCode;
    }

    public String getReagentName() {
        return reagentName;
    }

    public void setReagentName(String reagentName) {
        this.reagentName = reagentName;
    }

    public String getSpecification() {
        return specification;
    }

    public void setSpecification(String specification) {
        this.specification = specification;
    }

    public BigDecimal getQuantity() {
        return quantity;
    }

    public void setQuantity(BigDecimal quantity) {
        this.quantity = quantity;
    }

    public BigDecimal getArrivedQty() {
        return arrivedQty;
    }

    public void setArrivedQty(BigDecimal arrivedQty) {
        this.arrivedQty = arrivedQty;
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

    public BigDecimal getAmount() {
        return amount;
    }

    public void setAmount(BigDecimal amount) {
        this.amount = amount;
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

}
