package com.lis.reagent.vo;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 单个供应商对某试剂的历史价格聚合行。
 *
 * <p>由 {@code SupplierMapper.comparePriceByReagent()} 从历史采购订单明细聚合得出，
 * 是 {@link PriceCompareVO} 的行元素。</p>
 *
 * @author LIS Team
 */
public class SupplierPriceVO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 供应商 ID */
    private Long supplierId;
    /** 供应商名称 */
    private String supplierName;
    /** 试剂 ID */
    private Long reagentId;
    /** 最近一次成交单价 */
    private BigDecimal latestPrice;
    /** 历史最低单价 */
    private BigDecimal minPrice;
    /** 历史最高单价 */
    private BigDecimal maxPrice;
    /** 历史平均单价 */
    private BigDecimal avgPrice;
    /** 历史成交次数 */
    private Integer orderCount;
    /** 历史累计采购数量 */
    private BigDecimal totalQuantity;
    /** 最近一次成交日期 */
    private LocalDate latestOrderDate;
    /** 供应商综合评分（U-8：质量40% + 交付40% + 价格20%） */
    private BigDecimal score;
    /** 资质状态：0待审 1有效 2过期 */
    private Integer qualStatus;

    public SupplierPriceVO() {
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

    public Long getReagentId() {
        return reagentId;
    }

    public void setReagentId(Long reagentId) {
        this.reagentId = reagentId;
    }

    public BigDecimal getLatestPrice() {
        return latestPrice;
    }

    public void setLatestPrice(BigDecimal latestPrice) {
        this.latestPrice = latestPrice;
    }

    public BigDecimal getMinPrice() {
        return minPrice;
    }

    public void setMinPrice(BigDecimal minPrice) {
        this.minPrice = minPrice;
    }

    public BigDecimal getMaxPrice() {
        return maxPrice;
    }

    public void setMaxPrice(BigDecimal maxPrice) {
        this.maxPrice = maxPrice;
    }

    public BigDecimal getAvgPrice() {
        return avgPrice;
    }

    public void setAvgPrice(BigDecimal avgPrice) {
        this.avgPrice = avgPrice;
    }

    public Integer getOrderCount() {
        return orderCount;
    }

    public void setOrderCount(Integer orderCount) {
        this.orderCount = orderCount;
    }

    public BigDecimal getTotalQuantity() {
        return totalQuantity;
    }

    public void setTotalQuantity(BigDecimal totalQuantity) {
        this.totalQuantity = totalQuantity;
    }

    public LocalDate getLatestOrderDate() {
        return latestOrderDate;
    }

    public void setLatestOrderDate(LocalDate latestOrderDate) {
        this.latestOrderDate = latestOrderDate;
    }

    public BigDecimal getScore() {
        return score;
    }

    public void setScore(BigDecimal score) {
        this.score = score;
    }

    public Integer getQualStatus() {
        return qualStatus;
    }

    public void setQualStatus(Integer qualStatus) {
        this.qualStatus = qualStatus;
    }
}
