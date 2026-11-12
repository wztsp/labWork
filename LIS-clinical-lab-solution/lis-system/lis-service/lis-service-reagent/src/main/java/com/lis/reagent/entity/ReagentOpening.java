package com.lis.reagent.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 试剂开瓶记录
 */
@TableName("lis_reagent_opening")
public class ReagentOpening {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long reagentId;
    /** 试剂名称(非数据库字段) */
    @com.baomidou.mybatisplus.annotation.TableField(exist = false)
    private String reagentName;
    private Long stockId;
    private String lotNo;
    private LocalDateTime openTime;
    /** 开瓶效期 */
    private LocalDate openedExpiry;
    /** 1正常 0已过期 2已闭封 3已耗尽 */
    private Integer status;
    private Long operatorId;
    private String operatorName;
    private String remark;
    private LocalDateTime createTime;

    // ===== 开封业务扩展字段 =====

    /** 开封类型：1首次 2二次 3分包 4危化品 */
    private Integer openingType;
    /** 来源记录ID（二次开封/分包指向父记录） */
    private Long parentId;
    /** 分包序号，从1开始 */
    private Integer packNo;
    /** 分包总份数 */
    private Integer packTotal;
    /** 危化品需求数量 */
    private BigDecimal demandQty;
    /** 危化品审批状态：0待审 1通过 2驳回 */
    private Integer approvalStatus;
    /** 审批人ID */
    private Long approverId;
    /** 审批人姓名 */
    private String approverName;
    /** 审批时间 */
    private LocalDateTime approveTime;
    /** 审批意见 */
    private String approveOpinion;
    /** 闭封时间 */
    private LocalDateTime sealTime;
    /** 闭封操作人 */
    private String sealOperator;
    /** 耗尽时间 */
    private LocalDateTime exhaustTime;
    /** 闭封状态：0未闭封 1已闭封 */
    private Integer sealStatus;

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

    public Long getStockId() {
        return stockId;
    }

    public void setStockId(Long stockId) {
        this.stockId = stockId;
    }

    public String getLotNo() {
        return lotNo;
    }

    public void setLotNo(String lotNo) {
        this.lotNo = lotNo;
    }

    public LocalDateTime getOpenTime() {
        return openTime;
    }

    public void setOpenTime(LocalDateTime openTime) {
        this.openTime = openTime;
    }

    public LocalDate getOpenedExpiry() {
        return openedExpiry;
    }

    public void setOpenedExpiry(LocalDate openedExpiry) {
        this.openedExpiry = openedExpiry;
    }

    public Integer getStatus() {
        return status;
    }

    public void setStatus(Integer status) {
        this.status = status;
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

    public LocalDateTime getCreateTime() {
        return createTime;
    }

    public void setCreateTime(LocalDateTime createTime) {
        this.createTime = createTime;
    }

    // ===== 开封业务扩展字段访问器 =====

    public Integer getOpeningType() {
        return openingType;
    }

    public void setOpeningType(Integer openingType) {
        this.openingType = openingType;
    }

    public Long getParentId() {
        return parentId;
    }

    public void setParentId(Long parentId) {
        this.parentId = parentId;
    }

    public Integer getPackNo() {
        return packNo;
    }

    public void setPackNo(Integer packNo) {
        this.packNo = packNo;
    }

    public Integer getPackTotal() {
        return packTotal;
    }

    public void setPackTotal(Integer packTotal) {
        this.packTotal = packTotal;
    }

    public BigDecimal getDemandQty() {
        return demandQty;
    }

    public void setDemandQty(BigDecimal demandQty) {
        this.demandQty = demandQty;
    }

    public Integer getApprovalStatus() {
        return approvalStatus;
    }

    public void setApprovalStatus(Integer approvalStatus) {
        this.approvalStatus = approvalStatus;
    }

    public Long getApproverId() {
        return approverId;
    }

    public void setApproverId(Long approverId) {
        this.approverId = approverId;
    }

    public String getApproverName() {
        return approverName;
    }

    public void setApproverName(String approverName) {
        this.approverName = approverName;
    }

    public LocalDateTime getApproveTime() {
        return approveTime;
    }

    public void setApproveTime(LocalDateTime approveTime) {
        this.approveTime = approveTime;
    }

    public String getApproveOpinion() {
        return approveOpinion;
    }

    public void setApproveOpinion(String approveOpinion) {
        this.approveOpinion = approveOpinion;
    }

    public LocalDateTime getSealTime() {
        return sealTime;
    }

    public void setSealTime(LocalDateTime sealTime) {
        this.sealTime = sealTime;
    }

    public String getSealOperator() {
        return sealOperator;
    }

    public void setSealOperator(String sealOperator) {
        this.sealOperator = sealOperator;
    }

    public LocalDateTime getExhaustTime() {
        return exhaustTime;
    }

    public void setExhaustTime(LocalDateTime exhaustTime) {
        this.exhaustTime = exhaustTime;
    }

    public Integer getSealStatus() {
        return sealStatus;
    }

    public void setSealStatus(Integer sealStatus) {
        this.sealStatus = sealStatus;
    }

}
