package com.lis.reagent.vo;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 供应商评分展示对象（T-16 / R-11）。
 *
 * <p>准时率、拒收率、自动分、人工分、综合分均为 0-100 的百分制数值；
 * 手写 getter/setter，不使用 Lombok。</p>
 *
 * @author LIS Team
 */
public class SupplierScoreVO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 供应商 ID */
    private Long supplierId;
    /** 供应商名称 */
    private String supplierName;
    /** 订单数（已下单/部分到货/已到货 status IN(1,2,3) 的订单总数） */
    private long orderCount;
    /** 准时率 0-100 */
    private BigDecimal onTimeRate;
    /** 拒收率 0-100 */
    private BigDecimal rejectRate;
    /** 自动分 */
    private BigDecimal autoScore;
    /** 人工分 */
    private BigDecimal manualScore;
    /** 综合分 */
    private BigDecimal compositeScore;
    /** 最近一次下单日 */
    private LocalDate lastOrderDate;

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

    public long getOrderCount() {
        return orderCount;
    }

    public void setOrderCount(long orderCount) {
        this.orderCount = orderCount;
    }

    public BigDecimal getOnTimeRate() {
        return onTimeRate;
    }

    public void setOnTimeRate(BigDecimal onTimeRate) {
        this.onTimeRate = onTimeRate;
    }

    public BigDecimal getRejectRate() {
        return rejectRate;
    }

    public void setRejectRate(BigDecimal rejectRate) {
        this.rejectRate = rejectRate;
    }

    public BigDecimal getAutoScore() {
        return autoScore;
    }

    public void setAutoScore(BigDecimal autoScore) {
        this.autoScore = autoScore;
    }

    public BigDecimal getManualScore() {
        return manualScore;
    }

    public void setManualScore(BigDecimal manualScore) {
        this.manualScore = manualScore;
    }

    public BigDecimal getCompositeScore() {
        return compositeScore;
    }

    public void setCompositeScore(BigDecimal compositeScore) {
        this.compositeScore = compositeScore;
    }

    public LocalDate getLastOrderDate() {
        return lastOrderDate;
    }

    public void setLastOrderDate(LocalDate lastOrderDate) {
        this.lastOrderDate = lastOrderDate;
    }
}