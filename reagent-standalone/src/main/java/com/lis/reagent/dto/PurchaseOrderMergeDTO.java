package com.lis.reagent.dto;

import java.io.Serializable;
import java.time.LocalDate;
import java.util.List;

/**
 * 采购订单合并入参：多张已批准申请合并为一张订单。
 *
 * @author LIS Team
 */
public class PurchaseOrderMergeDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 待合并的采购申请 ID 列表，至少一张 */
    private List<Long> applyIds;
    /** 供应商 ID，必填 */
    private Long supplierId;
    /** 供应商名称快照 */
    private String supplierName;
    /** 期望到货日期 */
    private LocalDate expectDate;
    /** 操作人 ID */
    private Long operatorId;
    /** 操作人姓名 */
    private String operatorName;
    /** 备注 */
    private String remark;

    public PurchaseOrderMergeDTO() {
    }

    public List<Long> getApplyIds() {
        return applyIds;
    }

    public void setApplyIds(List<Long> applyIds) {
        this.applyIds = applyIds;
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

    public LocalDate getExpectDate() {
        return expectDate;
    }

    public void setExpectDate(LocalDate expectDate) {
        this.expectDate = expectDate;
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
