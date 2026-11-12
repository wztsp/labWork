package com.lis.reagent.vo;

import java.io.Serializable;
import java.math.BigDecimal;

/**
 * 库存不足预警行。
 *
 * <p>命名沿用设计文档 §6.4 的 {@code LowStockAlertDTO}（放在 {@code vo} 包下），
 * 保持与设计清单一致，避免前端接口文档对不上。</p>
 *
 * <p><b>阈值口径</b>：统一以 {@code lis_reagent.min_stock} 为准（主理人 Q4）。
 * 旧的 {@code safety_stock} 已废弃，不参与判定。</p>
 *
 * @author LIS Team
 */
public class LowStockAlertDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 试剂 ID */
    private Long reagentId;
    /** 试剂编码 */
    private String reagentCode;
    /** 试剂名称 */
    private String reagentName;
    /** 规格 */
    private String specification;
    /** 单位 */
    private String unit;
    /** 分类 ID */
    private Long categoryId;
    /** 分类名称 */
    private String categoryName;
    /** 仓库 ID；null 表示跨全部仓库汇总 */
    private Long warehouseId;
    /** 仓库名称 */
    private String warehouseName;
    /** 当前可用库存合计 */
    private BigDecimal currentQty;
    /** 库存下限阈值（min_stock） */
    private BigDecimal minStock;
    /** 缺口 = minStock - currentQty，恒为正数 */
    private BigDecimal gapQty;
    /** 是否危险品：0否 1是 */
    private Integer hazardFlag;
    /** 建议补货数量 = maxStock - currentQty；未配置上限时等于 gapQty */
    private BigDecimal suggestQty;

    public LowStockAlertDTO() {
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

    public String getUnit() {
        return unit;
    }

    public void setUnit(String unit) {
        this.unit = unit;
    }

    public Long getCategoryId() {
        return categoryId;
    }

    public void setCategoryId(Long categoryId) {
        this.categoryId = categoryId;
    }

    public String getCategoryName() {
        return categoryName;
    }

    public void setCategoryName(String categoryName) {
        this.categoryName = categoryName;
    }

    public Long getWarehouseId() {
        return warehouseId;
    }

    public void setWarehouseId(Long warehouseId) {
        this.warehouseId = warehouseId;
    }

    public String getWarehouseName() {
        return warehouseName;
    }

    public void setWarehouseName(String warehouseName) {
        this.warehouseName = warehouseName;
    }

    public BigDecimal getCurrentQty() {
        return currentQty;
    }

    public void setCurrentQty(BigDecimal currentQty) {
        this.currentQty = currentQty;
    }

    public BigDecimal getMinStock() {
        return minStock;
    }

    public void setMinStock(BigDecimal minStock) {
        this.minStock = minStock;
    }

    public BigDecimal getGapQty() {
        return gapQty;
    }

    public void setGapQty(BigDecimal gapQty) {
        this.gapQty = gapQty;
    }

    public Integer getHazardFlag() {
        return hazardFlag;
    }

    public void setHazardFlag(Integer hazardFlag) {
        this.hazardFlag = hazardFlag;
    }

    public BigDecimal getSuggestQty() {
        return suggestQty;
    }

    public void setSuggestQty(BigDecimal suggestQty) {
        this.suggestQty = suggestQty;
    }
}
