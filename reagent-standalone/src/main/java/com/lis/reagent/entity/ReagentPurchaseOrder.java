package com.lis.reagent.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 试剂采购订单。
 *
 * <p>{@code supplierName} 为冗余快照，供应商改名不影响历史订单。</p>
 *
 * <p>状态：0待下单 1已下单 2部分到货 3已到货 4已取消。
 * 到货状态由 {@code refreshOrderStatus()} 依据全部明细的 {@code arrivedQty} 汇总推导。</p>
 *
 * <p>手写 getter/setter，不使用 Lombok（C-10）。不声明 {@code deleted} 字段（C-9 复核结论）。</p>
 */
@TableName("lis_reagent_purchase_order")
public class ReagentPurchaseOrder {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 订单号 PO + yyyyMMdd + 4位序列，唯一 */
    private String orderNo;
    /** 来源申请单（合并单取第一张） */
    private Long applyId;
    private String applyNo;
    /** 合并来源申请 ID 列表，逗号分隔 */
    private String applyIds;
    /** 审批状态：0待审 1通过 2驳回 */
    private Integer approvalStatus;
    private LocalDateTime approvalTime;
    private String approvalOpinion;
    /** 审核状态：0未审核 1已审核 2已取消审核 */
    private Integer auditStatus;
    private Long auditorId;
    private String auditorName;
    private LocalDateTime auditTime;
    /** 供应商主数据ID */
    private Long supplierId;
    /** 冗余快照 */
    private String supplierName;
    private Long deptId;
    private String deptName;
    private BigDecimal totalAmount;
    /** 期望到货日 */
    private LocalDate expectDate;
    /** 下单日 */
    private LocalDate orderDate;
    /** 0待下单 1已下单 2部分到货 3已到货 4已取消 */
    private Integer status;
    private Long operatorId;
    private String operatorName;
    /** 三方卫材对接方编码，未对接为空 */
    private String thirdPartyCode;
    /** 对方系统订单号（下单回执） */
    private String remoteOrderNo;
    private String remark;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;

    /** 明细列表（非数据库字段，详情组装用） */
    @TableField(exist = false)
    private List<ReagentPurchaseOrderItem> items;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getOrderNo() {
        return orderNo;
    }

    public void setOrderNo(String orderNo) {
        this.orderNo = orderNo;
    }

    public Long getApplyId() {
        return applyId;
    }

    public void setApplyId(Long applyId) {
        this.applyId = applyId;
    }

    public String getApplyNo() {
        return applyNo;
    }

    public void setApplyNo(String applyNo) {
        this.applyNo = applyNo;
    }

    public String getApplyIds() {
        return applyIds;
    }

    public void setApplyIds(String applyIds) {
        this.applyIds = applyIds;
    }

    public Integer getApprovalStatus() {
        return approvalStatus;
    }

    public void setApprovalStatus(Integer approvalStatus) {
        this.approvalStatus = approvalStatus;
    }

    public LocalDateTime getApprovalTime() {
        return approvalTime;
    }

    public void setApprovalTime(LocalDateTime approvalTime) {
        this.approvalTime = approvalTime;
    }

    public String getApprovalOpinion() {
        return approvalOpinion;
    }

    public void setApprovalOpinion(String approvalOpinion) {
        this.approvalOpinion = approvalOpinion;
    }

    public Integer getAuditStatus() {
        return auditStatus;
    }

    public void setAuditStatus(Integer auditStatus) {
        this.auditStatus = auditStatus;
    }

    public Long getAuditorId() {
        return auditorId;
    }

    public void setAuditorId(Long auditorId) {
        this.auditorId = auditorId;
    }

    public String getAuditorName() {
        return auditorName;
    }

    public void setAuditorName(String auditorName) {
        this.auditorName = auditorName;
    }

    public LocalDateTime getAuditTime() {
        return auditTime;
    }

    public void setAuditTime(LocalDateTime auditTime) {
        this.auditTime = auditTime;
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

    public Long getDeptId() {
        return deptId;
    }

    public void setDeptId(Long deptId) {
        this.deptId = deptId;
    }

    public String getDeptName() {
        return deptName;
    }

    public void setDeptName(String deptName) {
        this.deptName = deptName;
    }

    public BigDecimal getTotalAmount() {
        return totalAmount;
    }

    public void setTotalAmount(BigDecimal totalAmount) {
        this.totalAmount = totalAmount;
    }

    public LocalDate getExpectDate() {
        return expectDate;
    }

    public void setExpectDate(LocalDate expectDate) {
        this.expectDate = expectDate;
    }

    public LocalDate getOrderDate() {
        return orderDate;
    }

    public void setOrderDate(LocalDate orderDate) {
        this.orderDate = orderDate;
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

    public String getThirdPartyCode() {
        return thirdPartyCode;
    }

    public void setThirdPartyCode(String thirdPartyCode) {
        this.thirdPartyCode = thirdPartyCode;
    }

    public String getRemoteOrderNo() {
        return remoteOrderNo;
    }

    public void setRemoteOrderNo(String remoteOrderNo) {
        this.remoteOrderNo = remoteOrderNo;
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

    public LocalDateTime getUpdateTime() {
        return updateTime;
    }

    public void setUpdateTime(LocalDateTime updateTime) {
        this.updateTime = updateTime;
    }

    public List<ReagentPurchaseOrderItem> getItems() {
        return items;
    }

    public void setItems(List<ReagentPurchaseOrderItem> items) {
        this.items = items;
    }

}
