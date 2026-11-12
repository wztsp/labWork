package com.lis.reagent.dto;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * 扫码入库入参：规则生成 / 单码 / 条码别名三种场景共用。
 *
 * @author LIS Team
 */
public class InboundScanDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 规则生成条码前缀 */
    private String prefix;
    /** 规则生成起始序号 */
    private Integer startSeq;
    /** 规则生成条码数量 */
    private Integer count;
    /** 扫码得到的条码集合（单码场景） */
    private List<String> barcodes;
    /** 待解析的条码集合（别名场景） */
    private List<String> codes;
    /** 试剂 ID */
    private Long reagentId;
    /** 批次号 */
    private String batchNo;
    /** 有效期 */
    private LocalDate expiryDate;
    /** 每条款码对应数量 */
    private BigDecimal quantity;
    /** 单位 */
    private String unit;
    /** 单价 */
    private BigDecimal purchasePrice;
    /** 仓库 ID */
    private Long warehouseId;
    /** 操作人 ID */
    private Long operatorId;
    /** 操作人姓名 */
    private String operatorName;
    /** 备注 */
    private String remark;

    public InboundScanDTO() {
    }

    public String getPrefix() {
        return prefix;
    }

    public void setPrefix(String prefix) {
        this.prefix = prefix;
    }

    public Integer getStartSeq() {
        return startSeq;
    }

    public void setStartSeq(Integer startSeq) {
        this.startSeq = startSeq;
    }

    public Integer getCount() {
        return count;
    }

    public void setCount(Integer count) {
        this.count = count;
    }

    public List<String> getBarcodes() {
        return barcodes;
    }

    public void setBarcodes(List<String> barcodes) {
        this.barcodes = barcodes;
    }

    public List<String> getCodes() {
        return codes;
    }

    public void setCodes(List<String> codes) {
        this.codes = codes;
    }

    public Long getReagentId() {
        return reagentId;
    }

    public void setReagentId(Long reagentId) {
        this.reagentId = reagentId;
    }

    public String getBatchNo() {
        return batchNo;
    }

    public void setBatchNo(String batchNo) {
        this.batchNo = batchNo;
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

    public BigDecimal getPurchasePrice() {
        return purchasePrice;
    }

    public void setPurchasePrice(BigDecimal purchasePrice) {
        this.purchasePrice = purchasePrice;
    }

    public Long getWarehouseId() {
        return warehouseId;
    }

    public void setWarehouseId(Long warehouseId) {
        this.warehouseId = warehouseId;
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
}
