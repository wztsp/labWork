package com.lis.reagent.vo;

import java.io.Serializable;
import java.math.BigDecimal;
import java.util.List;

/**
 * 某试剂的多供应商比价结果。
 *
 * @author LIS Team
 */
public class PriceCompareVO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 试剂 ID */
    private Long reagentId;
    /** 试剂编码 */
    private String reagentCode;
    /** 试剂名称 */
    private String reagentName;
    /** 规格 */
    private String specification;
    /** 各供应商价格行，按 latestPrice 升序 */
    private List<SupplierPriceVO> suppliers;
    /** 全部供应商中的最低单价 */
    private BigDecimal lowestPrice;
    /** 报出最低价的供应商 ID */
    private Long lowestSupplierId;
    /** 报出最低价的供应商名称 */
    private String lowestSupplierName;
    /** 综合评分最高的供应商 ID（评分口径见 U-8） */
    private Long bestScoreSupplierId;
    /** 综合评分最高的供应商名称 */
    private String bestScoreSupplierName;

    public PriceCompareVO() {
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

    public List<SupplierPriceVO> getSuppliers() {
        return suppliers;
    }

    public void setSuppliers(List<SupplierPriceVO> suppliers) {
        this.suppliers = suppliers;
    }

    public BigDecimal getLowestPrice() {
        return lowestPrice;
    }

    public void setLowestPrice(BigDecimal lowestPrice) {
        this.lowestPrice = lowestPrice;
    }

    public Long getLowestSupplierId() {
        return lowestSupplierId;
    }

    public void setLowestSupplierId(Long lowestSupplierId) {
        this.lowestSupplierId = lowestSupplierId;
    }

    public String getLowestSupplierName() {
        return lowestSupplierName;
    }

    public void setLowestSupplierName(String lowestSupplierName) {
        this.lowestSupplierName = lowestSupplierName;
    }

    public Long getBestScoreSupplierId() {
        return bestScoreSupplierId;
    }

    public void setBestScoreSupplierId(Long bestScoreSupplierId) {
        this.bestScoreSupplierId = bestScoreSupplierId;
    }

    public String getBestScoreSupplierName() {
        return bestScoreSupplierName;
    }

    public void setBestScoreSupplierName(String bestScoreSupplierName) {
        this.bestScoreSupplierName = bestScoreSupplierName;
    }
}
