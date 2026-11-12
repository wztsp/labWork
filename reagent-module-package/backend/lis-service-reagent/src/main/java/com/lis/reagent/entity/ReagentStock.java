package com.lis.reagent.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 试剂库存 (按批次)
 */
@TableName("lis_reagent_stock")
public class ReagentStock {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long reagentId;
    /** 试剂名称(非数据库字段) */
    @com.baomidou.mybatisplus.annotation.TableField(exist = false)
    private String reagentName;
    /** 试剂编码(非数据库字段) */
    @com.baomidou.mybatisplus.annotation.TableField(exist = false)
    private String reagentCode;
    private String lotNo;
    private String barcode;
    private LocalDate expiryDate;
    private BigDecimal quantity;
    private String location;
    /**
     * 仓库 ID。0 = 默认库房（待归集），采用哨兵值而非 NULL，
     * 因为 PostgreSQL 唯一索引中 NULL &lt;&gt; NULL，会使
     * uk_lis_reagent_stock_lot_wh(reagent_id, lot_no, warehouse_id) 失效。
     */
    private Long warehouseId;
    /** 0过期锁定 1可用 2已耗尽 */
    private Integer status;
    private LocalDateTime inStockTime;
    private LocalDateTime createTime;

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

    public String getReagentName() {
        return reagentName;
    }

    public void setReagentName(String reagentName) {
        this.reagentName = reagentName;
    }

    public String getReagentCode() {
        return reagentCode;
    }

    public void setReagentCode(String reagentCode) {
        this.reagentCode = reagentCode;
    }

    public String getLotNo() {
        return lotNo;
    }

    public void setLotNo(String lotNo) {
        this.lotNo = lotNo;
    }

    public String getBarcode() {
        return barcode;
    }

    public void setBarcode(String barcode) {
        this.barcode = barcode;
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

    public String getLocation() {
        return location;
    }

    public void setLocation(String location) {
        this.location = location;
    }

    public Long getWarehouseId() {
        return warehouseId;
    }

    public void setWarehouseId(Long warehouseId) {
        this.warehouseId = warehouseId;
    }

    public Integer getStatus() {
        return status;
    }

    public void setStatus(Integer status) {
        this.status = status;
    }

    public LocalDateTime getInStockTime() {
        return inStockTime;
    }

    public void setInStockTime(LocalDateTime inStockTime) {
        this.inStockTime = inStockTime;
    }

    public LocalDateTime getCreateTime() {
        return createTime;
    }

    public void setCreateTime(LocalDateTime createTime) {
        this.createTime = createTime;
    }

}
