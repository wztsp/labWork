package com.lis.reagent.dto;

import java.io.Serializable;
import java.time.LocalDate;

/**
 * 盘点单创建入参。
 *
 * <p>{@code warehouseId} 为 null 表示全仓盘点（主理人 U-10）；
 * 非 null 时按仓库物化路径子树精确匹配库存，替代原先的
 * {@code location LIKE warehouse} 文本模糊匹配。</p>
 *
 * @author LIS Team
 */
public class StockCheckCreateDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 盘点仓库 ID；null 表示全仓盘点 */
    private Long warehouseId;
    /** 是否包含子仓库，默认 true */
    private Boolean includeChildren = Boolean.TRUE;
    /** 盘点日期；为空取当天 */
    private LocalDate checkDate;
    /** 操作人 ID */
    private Long operatorId;
    /** 操作人姓名 */
    private String operatorName;
    /** 备注 */
    private String remark;

    public StockCheckCreateDTO() {
    }

    public Long getWarehouseId() {
        return warehouseId;
    }

    public void setWarehouseId(Long warehouseId) {
        this.warehouseId = warehouseId;
    }

    public Boolean getIncludeChildren() {
        return includeChildren;
    }

    public void setIncludeChildren(Boolean includeChildren) {
        this.includeChildren = includeChildren;
    }

    public LocalDate getCheckDate() {
        return checkDate;
    }

    public void setCheckDate(LocalDate checkDate) {
        this.checkDate = checkDate;
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
