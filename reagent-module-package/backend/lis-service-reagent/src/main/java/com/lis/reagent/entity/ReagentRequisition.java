package com.lis.reagent.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 试剂统一出入库业务单实体（领用 / 退库 / 报废 / 调拨 / 借用 / 归还）。
 *
 * <p>对应表 {@code lis_reagent_requisition}。六种单据共用一张表，
 * 由 {@link com.lis.reagent.enums.ReqType} 区分方向与库存记录类型，
 * 具体规格由纯 POJO {@code RequisitionTypeSpec} 描述。</p>
 *
 * <p>注意：本实体不含 {@code deleted} 字段。作废语义统一走 {@code status=5}，
 * 详见 DDL 附录 A 与设计文档 C-9。</p>
 *
 * @author LIS Team
 */
@TableName("lis_reagent_requisition")
public class ReagentRequisition implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 主键 */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 单据编号，规则 RQ + yyyyMMdd + 4 位序列 */
    private String reqNo;

    /** 单据类型：1领用 2退库 3报废 4调拨 5借用 6归还 */
    private Integer reqType;

    /** 归属科室 ID（sys_department.id 快照） */
    private Long deptId;

    /** 归属科室名称快照 */
    private String deptName;

    /** 申请人 ID */
    private Long applyUserId;

    /** 申请人姓名快照 */
    private String applyUserName;

    /** 状态：0草稿 1待审 2已审 3已执行 4已驳回 5已作废 */
    private Integer status;

    /** 审批人 ID */
    private Long approverId;

    /** 审批人姓名快照 */
    private String approverName;

    /** 审批时间 */
    private LocalDateTime approveTime;

    /** 审批意见 */
    private String approveOpinion;

    /** 执行人（实际操作库存的人）ID */
    private Long executorId;

    /** 执行人姓名快照 */
    private String executorName;

    /** 执行时间（库存实际变动时间） */
    private LocalDateTime executeTime;

    /** 源仓库 ID（出库类 / 调拨源） */
    private Long fromWarehouseId;

    /** 目标仓库 ID（入库类 / 调拨目标） */
    private Long toWarehouseId;

    /** 明细中是否含危险品：0否 1是。决定审批链是否追加安全员节点 */
    private Integer hazardFlag;

    /** 预计归还日期，仅 reqType=5（借用）使用 */
    private LocalDate expectReturnDate;

    /** 归还单指向被归还的借用单 ID，仅 reqType=6（归还）使用 */
    private Long sourceReqId;

    /** 事由；reqType=3（报废）时必填 */
    private String reason;

    /** 单据金额（成本归集用） */
    private BigDecimal totalAmount;

    /** 备注 */
    private String remark;

    /** 创建时间 */
    private LocalDateTime createTime;

    /** 更新时间 */
    private LocalDateTime updateTime;

    /** 明细列表，非数据库字段 */
    @TableField(exist = false)
    private List<ReagentRequisitionItem> items;

    public ReagentRequisition() {
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getReqNo() {
        return reqNo;
    }

    public void setReqNo(String reqNo) {
        this.reqNo = reqNo;
    }

    public Integer getReqType() {
        return reqType;
    }

    public void setReqType(Integer reqType) {
        this.reqType = reqType;
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

    public Integer getStatus() {
        return status;
    }

    public void setStatus(Integer status) {
        this.status = status;
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

    public Long getExecutorId() {
        return executorId;
    }

    public void setExecutorId(Long executorId) {
        this.executorId = executorId;
    }

    public String getExecutorName() {
        return executorName;
    }

    public void setExecutorName(String executorName) {
        this.executorName = executorName;
    }

    public LocalDateTime getExecuteTime() {
        return executeTime;
    }

    public void setExecuteTime(LocalDateTime executeTime) {
        this.executeTime = executeTime;
    }

    public Long getFromWarehouseId() {
        return fromWarehouseId;
    }

    public void setFromWarehouseId(Long fromWarehouseId) {
        this.fromWarehouseId = fromWarehouseId;
    }

    public Long getToWarehouseId() {
        return toWarehouseId;
    }

    public void setToWarehouseId(Long toWarehouseId) {
        this.toWarehouseId = toWarehouseId;
    }

    public Integer getHazardFlag() {
        return hazardFlag;
    }

    public void setHazardFlag(Integer hazardFlag) {
        this.hazardFlag = hazardFlag;
    }

    public LocalDate getExpectReturnDate() {
        return expectReturnDate;
    }

    public void setExpectReturnDate(LocalDate expectReturnDate) {
        this.expectReturnDate = expectReturnDate;
    }

    public Long getSourceReqId() {
        return sourceReqId;
    }

    public void setSourceReqId(Long sourceReqId) {
        this.sourceReqId = sourceReqId;
    }

    public String getReason() {
        return reason;
    }

    public void setReason(String reason) {
        this.reason = reason;
    }

    public BigDecimal getTotalAmount() {
        return totalAmount;
    }

    public void setTotalAmount(BigDecimal totalAmount) {
        this.totalAmount = totalAmount;
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

    public List<ReagentRequisitionItem> getItems() {
        return items;
    }

    public void setItems(List<ReagentRequisitionItem> items) {
        this.items = items;
    }
}
