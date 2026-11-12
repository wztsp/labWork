package com.lis.reagent.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 试剂字典
 */
@TableName("lis_reagent")
public class Reagent {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String reagentCode;
    private String reagentName;
    private String specification;
    private String manufacturer;
    private String casNo;
    private String storageCond;
    private String unit;
    private Long instrumentId;
    private Long testItemId;
    /**
     * 【已废弃】安全库存。三阈值字段统一后以 {@link #minStock} 为准。
     * 保留仅为兼容存量数据，新代码不再写入。
     *
     * @deprecated 请使用 {@link #getMinStock()}
     */
    @Deprecated
    private BigDecimal safetyStock;
    /** 分类ID */
    private Long categoryId;
    /** 厂商ID */
    private Long manufacturerId;
    /** 注册证号 */
    private String regCertNo;
    /** 预期用途 */
    private String expectedUse;
    /** 采购价 */
    private BigDecimal pricePurchase;
    /** 销售价 */
    private BigDecimal priceSelling;
    /** 每测试用量 */
    private BigDecimal usagePerTest;
    /** 开瓶效期(天) */
    private Integer openedExpiryDays;
    /** 库存下限 */
    private BigDecimal minStock;
    /** 库存上限 */
    private BigDecimal maxStock;
    /** 供应商名称（旧字段，保留冗余快照） */
    private String supplier;
    private Integer status;
    private LocalDateTime createTime;

    // ===== 试剂管理增量扩展字段（对应 add-reagent-enhance.sql 第 15 节）=====

    /** 供应商主数据 ID，关联 lis_supplier.id */
    private Long supplierId;
    /** 是否危险品：0普通 1危险品 */
    private Integer hazardFlag;
    /** 危险等级：易燃 / 易爆 / 剧毒 / 腐蚀 / 放射性 */
    private String hazardClass;
    /** MSDS 要点文本（U-9：不做文件上传，仅文本 + 链接） */
    private String msdsSummary;
    /** MSDS 外部链接 */
    private String msdsUrl;
    /** 储存温度下限(℃)，与 storageCond 文本并存 */
    private BigDecimal storageTempMin;
    /** 储存温度上限(℃) */
    private BigDecimal storageTempMax;

    // ===== 试剂基础字段补齐（库存预警 / 采购建议）=====

    /** 最佳库存（采购建议目标量） */
    private BigDecimal optimalStock;
    /** 采购周期(天) */
    private Integer purchaseCycleDays;
    /** 条码别名（扫码识别用） */
    private String barcodeAlias;
    /** 质控品标志：0否 1是 */
    private Integer qcFlag;
    /** 质控品包装数量 */
    private Integer qcPackCount;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
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

    public String getManufacturer() {
        return manufacturer;
    }

    public void setManufacturer(String manufacturer) {
        this.manufacturer = manufacturer;
    }

    public String getCasNo() {
        return casNo;
    }

    public void setCasNo(String casNo) {
        this.casNo = casNo;
    }

    public String getStorageCond() {
        return storageCond;
    }

    public void setStorageCond(String storageCond) {
        this.storageCond = storageCond;
    }

    public String getUnit() {
        return unit;
    }

    public void setUnit(String unit) {
        this.unit = unit;
    }

    public Long getInstrumentId() {
        return instrumentId;
    }

    public void setInstrumentId(Long instrumentId) {
        this.instrumentId = instrumentId;
    }

    public Long getTestItemId() {
        return testItemId;
    }

    public void setTestItemId(Long testItemId) {
        this.testItemId = testItemId;
    }

    /**
     * @deprecated 请使用 {@link #getMinStock()}，安全库存语义已统一到 minStock
     */
    @Deprecated
    public BigDecimal getSafetyStock() {
        return safetyStock;
    }

    /**
     * @deprecated 请使用 {@link #setMinStock(BigDecimal)}，新代码不再写入本字段
     */
    @Deprecated
    public void setSafetyStock(BigDecimal safetyStock) {
        this.safetyStock = safetyStock;
    }

    public Long getCategoryId() {
        return categoryId;
    }

    public void setCategoryId(Long categoryId) {
        this.categoryId = categoryId;
    }

    public Long getManufacturerId() {
        return manufacturerId;
    }

    public void setManufacturerId(Long manufacturerId) {
        this.manufacturerId = manufacturerId;
    }

    public String getRegCertNo() {
        return regCertNo;
    }

    public void setRegCertNo(String regCertNo) {
        this.regCertNo = regCertNo;
    }

    public String getExpectedUse() {
        return expectedUse;
    }

    public void setExpectedUse(String expectedUse) {
        this.expectedUse = expectedUse;
    }

    public BigDecimal getPricePurchase() {
        return pricePurchase;
    }

    public void setPricePurchase(BigDecimal pricePurchase) {
        this.pricePurchase = pricePurchase;
    }

    public BigDecimal getPriceSelling() {
        return priceSelling;
    }

    public void setPriceSelling(BigDecimal priceSelling) {
        this.priceSelling = priceSelling;
    }

    public BigDecimal getUsagePerTest() {
        return usagePerTest;
    }

    public void setUsagePerTest(BigDecimal usagePerTest) {
        this.usagePerTest = usagePerTest;
    }

    public Integer getOpenedExpiryDays() {
        return openedExpiryDays;
    }

    public void setOpenedExpiryDays(Integer openedExpiryDays) {
        this.openedExpiryDays = openedExpiryDays;
    }

    public BigDecimal getMinStock() {
        return minStock;
    }

    public void setMinStock(BigDecimal minStock) {
        this.minStock = minStock;
    }

    public BigDecimal getMaxStock() {
        return maxStock;
    }

    public void setMaxStock(BigDecimal maxStock) {
        this.maxStock = maxStock;
    }

    public String getSupplier() {
        return supplier;
    }

    public void setSupplier(String supplier) {
        this.supplier = supplier;
    }

    public Integer getStatus() {
        return status;
    }

    public void setStatus(Integer status) {
        this.status = status;
    }

    public LocalDateTime getCreateTime() {
        return createTime;
    }

    public void setCreateTime(LocalDateTime createTime) {
        this.createTime = createTime;
    }

    // ===== 试剂管理增量扩展字段访问器 =====

    public Long getSupplierId() {
        return supplierId;
    }

    public void setSupplierId(Long supplierId) {
        this.supplierId = supplierId;
    }

    public Integer getHazardFlag() {
        return hazardFlag;
    }

    public void setHazardFlag(Integer hazardFlag) {
        this.hazardFlag = hazardFlag;
    }

    public String getHazardClass() {
        return hazardClass;
    }

    public void setHazardClass(String hazardClass) {
        this.hazardClass = hazardClass;
    }

    public String getMsdsSummary() {
        return msdsSummary;
    }

    public void setMsdsSummary(String msdsSummary) {
        this.msdsSummary = msdsSummary;
    }

    public String getMsdsUrl() {
        return msdsUrl;
    }

    public void setMsdsUrl(String msdsUrl) {
        this.msdsUrl = msdsUrl;
    }

    public BigDecimal getStorageTempMin() {
        return storageTempMin;
    }

    public void setStorageTempMin(BigDecimal storageTempMin) {
        this.storageTempMin = storageTempMin;
    }

    public BigDecimal getStorageTempMax() {
        return storageTempMax;
    }

    public void setStorageTempMax(BigDecimal storageTempMax) {
        this.storageTempMax = storageTempMax;
    }

    // ===== 试剂基础字段补齐访问器 =====

    public BigDecimal getOptimalStock() {
        return optimalStock;
    }

    public void setOptimalStock(BigDecimal optimalStock) {
        this.optimalStock = optimalStock;
    }

    public Integer getPurchaseCycleDays() {
        return purchaseCycleDays;
    }

    public void setPurchaseCycleDays(Integer purchaseCycleDays) {
        this.purchaseCycleDays = purchaseCycleDays;
    }

    public String getBarcodeAlias() {
        return barcodeAlias;
    }

    public void setBarcodeAlias(String barcodeAlias) {
        this.barcodeAlias = barcodeAlias;
    }

    public Integer getQcFlag() {
        return qcFlag;
    }

    public void setQcFlag(Integer qcFlag) {
        this.qcFlag = qcFlag;
    }

    public Integer getQcPackCount() {
        return qcPackCount;
    }

    public void setQcPackCount(Integer qcPackCount) {
        this.qcPackCount = qcPackCount;
    }

}
