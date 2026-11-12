package com.lis.reagent.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 试剂出入库业务单明细实体。
 *
 * <p>对应表 {@code lis_reagent_requisition_item}。
 * {@code quantity} 恒为正数，出入方向由主单 {@code reqType} 决定，
 * 避免正负号在多处取反导致的符号错误。</p>
 *
 * @author LIS Team
 */
@TableName("lis_reagent_requisition_item")
public class ReagentRequisitionItem implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 主键 */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 所属业务单 ID */
    private Long reqId;

    /** 试剂 ID */
    private Long reagentId;

    /** 试剂编码快照 */
    private String reagentCode;

    /** 试剂名称快照 */
    private String reagentName;

    /** 指定批次库存 ID（出库类必填） */
    private Long stockId;

    /** 批号快照 */
    private String lotNo;

    /** 有效期快照 */
    private LocalDate expiryDate;

    /** 数量，恒为正数 */
    private BigDecimal quantity;

    /** 单位 */
    private String unit;

    /** 成本单价快照 */
    private BigDecimal unitPrice;

    /** 金额 = quantity * unitPrice */
    private BigDecimal amount;

    /** 是否危险品：0否 1是 */
    private Integer hazardFlag;

    /** 备注 */
    private String remark;

    /** 创建时间 */
    private LocalDateTime createTime;

    public ReagentRequisitionItem() {
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getReqId() {
        return reqId;
    }

    public void setReqId(Long reqId) {
        this.reqId = reqId;
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

    public Integer getHazardFlag() {
        return hazardFlag;
    }

    public void setHazardFlag(Integer hazardFlag) {
        this.hazardFlag = hazardFlag;
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
