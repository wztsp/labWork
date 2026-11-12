package com.lis.reagent.dto;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * 采购申请创建 / 编辑入参。
 *
 * <p>{@code id} 为空表示新建，非空表示编辑草稿（仅草稿 / 已驳回可编辑，
 * 由 {@code PurchaseApplyStateMachine.assertEditable} 把关）。</p>
 *
 * @author LIS Team
 */
public class ApplyCreateDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 申请单 ID，新建时为 null */
    private Long id;
    /** 申请科室 ID */
    private Long applyDeptId;
    /** 申请科室名称 */
    private String applyDeptName;
    /** 申请人 ID */
    private Long applyUserId;
    /** 申请人姓名 */
    private String applyUserName;
    /** 预算年度，为空时取当前年度 */
    private Integer budgetYear;
    /** 是否加急：0否 1是 */
    private Integer urgentFlag;
    /** 期望到货日期 */
    private LocalDate expectDate;
    /** 备注 */
    private String remark;
    /** 明细列表，至少一条 */
    private List<ApplyItemDTO> items;

    public ApplyCreateDTO() {
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getApplyDeptId() {
        return applyDeptId;
    }

    public void setApplyDeptId(Long applyDeptId) {
        this.applyDeptId = applyDeptId;
    }

    public String getApplyDeptName() {
        return applyDeptName;
    }

    public void setApplyDeptName(String applyDeptName) {
        this.applyDeptName = applyDeptName;
    }

    public Long getApplyUserId() {
        return applyUserId;
    }

    public void setApplyUserId(Long applyUserId) {
        this.applyUserId = applyUserId;
    }

    public String getApplyUserName() {
        return applyUserName;
    }

    public void setApplyUserName(String applyUserName) {
        this.applyUserName = applyUserName;
    }

    public Integer getBudgetYear() {
        return budgetYear;
    }

    public void setBudgetYear(Integer budgetYear) {
        this.budgetYear = budgetYear;
    }

    public Integer getUrgentFlag() {
        return urgentFlag;
    }

    public void setUrgentFlag(Integer urgentFlag) {
        this.urgentFlag = urgentFlag;
    }

    public LocalDate getExpectDate() {
        return expectDate;
    }

    public void setExpectDate(LocalDate expectDate) {
        this.expectDate = expectDate;
    }

    public String getRemark() {
        return remark;
    }

    public void setRemark(String remark) {
        this.remark = remark;
    }

    public List<ApplyItemDTO> getItems() {
        return items;
    }

    public void setItems(List<ApplyItemDTO> items) {
        this.items = items;
    }

    /**
     * 采购申请明细入参。
     */
    public static class ApplyItemDTO implements Serializable {

        private static final long serialVersionUID = 1L;

        /** 明细 ID，新建时为 null */
        private Long id;
        /** 试剂 ID，必填 */
        private Long reagentId;
        /** 申请数量，必填且必须大于 0 */
        private BigDecimal quantity;
        /** 单位 */
        private String unit;
        /** 预估单价；为空时由 Service 取试剂字典的 pricePurchase */
        private BigDecimal estimatedPrice;
        /** 备注 */
        private String remark;

        public ApplyItemDTO() {
        }

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

        public BigDecimal getEstimatedPrice() {
            return estimatedPrice;
        }

        public void setEstimatedPrice(BigDecimal estimatedPrice) {
            this.estimatedPrice = estimatedPrice;
        }

        public String getRemark() {
            return remark;
        }

        public void setRemark(String remark) {
            this.remark = remark;
        }
    }
}
