package com.lis.sample.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 标本
 */
@TableName("lis_sample")
public class Sample implements Serializable {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    /** 标本条码 */
    private String sampleBarcode;

    /** 标本号 (按仪器序号生成) */
    private String sampleNo;

    /** 核收仪器ID */
    private Long instrumentId;

    /** 检验申请ID */
    private Long orderId;

    /** 患者ID */
    private Long patientId;

    /** 就诊ID */
    private Long visitId;

    /** 患者姓名 */
    private String patientName;

    /** 标本类型 */
    private String sampleType;

    /** 容器类型 */
    private String containerType;

    /** 状态: 0已申请 1已采集 2已送检 3已签收 4已核收 5检测中 6已报告 7已退费 8已作废 9已拒收 */
    private Integer status;

    /** 是否急查: 0否 1是 */
    private Integer isUrgent;

    /** 采集时间 */
    private LocalDateTime collectTime;

    /** 送检时间 */
    private LocalDateTime deliverTime;

    /** 送检人 (姓名冗余) */
    private String deliverPerson;

    /** 送检人ID (关联人事员工 hr_staff.id) */
    private Long deliverPersonId;

    /** 签收时间 */
    private LocalDateTime signOffTime;

    /** 签收人 (姓名冗余) */
    private String signOffPerson;

    /** 签收人ID (关联人事员工 hr_staff.id) */
    private Long signOffPersonId;

    /** 拒收时间 */
    private LocalDateTime rejectTime;

    /** 拒收人 (姓名冗余) */
    private String rejectPerson;

    /** 拒收人ID (关联人事员工 hr_staff.id) */
    private Long rejectPersonId;

    /** 不合格类型 */
    private String rejectType;

    /** 拒收原因 */
    private String rejectReason;

    /** 拒收次数 */
    private Integer rejectCount;

    /** 就诊号 */
    private String visitNo;

    /** 核收时间 */
    private LocalDateTime receiveTime;

    /** 检测开始时间 */
    private LocalDateTime testStartTime;

    /** 报告时间 */
    private LocalDateTime reportTime;

    /** 操作员ID */
    private Long operatorId;

    /** 采集人ID */
    private Long collectorId;

    /** 科室 */
    private String department;

    /** 备注 */
    private String remark;

    /** 逻辑删除: 0未删 1已删 */
    @TableLogic
    private Integer deleted;

    /** 创建时间 */
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    /** 更新时间 */
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;

    /** 状态常量: 已申请 */
    public static final int STATUS_APPLIED = 0;
    /** 状态常量: 已采集 */
    public static final int STATUS_COLLECTED = 1;
    /** 状态常量: 已送检 */
    public static final int STATUS_DELIVERED = 2;
    /** 状态常量: 已签收 */
    public static final int STATUS_SIGNED = 3;
    /** 状态常量: 已核收 */
    public static final int STATUS_RECEIVED = 4;
    /** 状态常量: 检测中 */
    public static final int STATUS_TESTING = 5;
    /** 状态常量: 已报告 */
    public static final int STATUS_REPORTED = 6;
    /** 状态常量: 已退费 */
    public static final int STATUS_REFUNDED = 7;
    /** 状态常量: 已作废 */
    public static final int STATUS_VOIDED = 8;
    /** 状态常量: 已拒收 */
    public static final int STATUS_REJECTED = 9;

    /**
     * 状态文本描述
     */
    public static String statusText(int status) {
        return switch (status) {
            case STATUS_APPLIED -> "已申请";
            case STATUS_COLLECTED -> "已采集";
            case STATUS_DELIVERED -> "已送检";
            case STATUS_SIGNED -> "已签收";
            case STATUS_RECEIVED -> "已核收";
            case STATUS_TESTING -> "检测中";
            case STATUS_REPORTED -> "已报告";
            case STATUS_REFUNDED -> "已退费";
            case STATUS_VOIDED -> "已作废";
            case STATUS_REJECTED -> "已拒收";
            default -> "未知";
        };
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getSampleBarcode() {
        return sampleBarcode;
    }

    public void setSampleBarcode(String sampleBarcode) {
        this.sampleBarcode = sampleBarcode;
    }

    public String getSampleNo() {
        return sampleNo;
    }

    public void setSampleNo(String sampleNo) {
        this.sampleNo = sampleNo;
    }

    public Long getInstrumentId() {
        return instrumentId;
    }

    public void setInstrumentId(Long instrumentId) {
        this.instrumentId = instrumentId;
    }

    public Long getOrderId() {
        return orderId;
    }

    public void setOrderId(Long orderId) {
        this.orderId = orderId;
    }

    public Long getPatientId() {
        return patientId;
    }

    public void setPatientId(Long patientId) {
        this.patientId = patientId;
    }

    public Long getVisitId() {
        return visitId;
    }

    public void setVisitId(Long visitId) {
        this.visitId = visitId;
    }

    public String getPatientName() {
        return patientName;
    }

    public void setPatientName(String patientName) {
        this.patientName = patientName;
    }

    public String getSampleType() {
        return sampleType;
    }

    public void setSampleType(String sampleType) {
        this.sampleType = sampleType;
    }

    public String getContainerType() {
        return containerType;
    }

    public void setContainerType(String containerType) {
        this.containerType = containerType;
    }

    public Integer getStatus() {
        return status;
    }

    public void setStatus(Integer status) {
        this.status = status;
    }

    public Integer getIsUrgent() {
        return isUrgent;
    }

    public void setIsUrgent(Integer isUrgent) {
        this.isUrgent = isUrgent;
    }

    public LocalDateTime getCollectTime() {
        return collectTime;
    }

    public void setCollectTime(LocalDateTime collectTime) {
        this.collectTime = collectTime;
    }

    public LocalDateTime getDeliverTime() {
        return deliverTime;
    }

    public void setDeliverTime(LocalDateTime deliverTime) {
        this.deliverTime = deliverTime;
    }

    public String getDeliverPerson() {
        return deliverPerson;
    }

    public void setDeliverPerson(String deliverPerson) {
        this.deliverPerson = deliverPerson;
    }

    public Long getDeliverPersonId() {
        return deliverPersonId;
    }

    public void setDeliverPersonId(Long deliverPersonId) {
        this.deliverPersonId = deliverPersonId;
    }

    public LocalDateTime getSignOffTime() {
        return signOffTime;
    }

    public void setSignOffTime(LocalDateTime signOffTime) {
        this.signOffTime = signOffTime;
    }

    public String getSignOffPerson() {
        return signOffPerson;
    }

    public void setSignOffPerson(String signOffPerson) {
        this.signOffPerson = signOffPerson;
    }

    public Long getSignOffPersonId() {
        return signOffPersonId;
    }

    public void setSignOffPersonId(Long signOffPersonId) {
        this.signOffPersonId = signOffPersonId;
    }

    public LocalDateTime getRejectTime() {
        return rejectTime;
    }

    public void setRejectTime(LocalDateTime rejectTime) {
        this.rejectTime = rejectTime;
    }

    public String getRejectPerson() {
        return rejectPerson;
    }

    public void setRejectPerson(String rejectPerson) {
        this.rejectPerson = rejectPerson;
    }

    public Long getRejectPersonId() {
        return rejectPersonId;
    }

    public void setRejectPersonId(Long rejectPersonId) {
        this.rejectPersonId = rejectPersonId;
    }

    public String getRejectType() {
        return rejectType;
    }

    public void setRejectType(String rejectType) {
        this.rejectType = rejectType;
    }

    public String getRejectReason() {
        return rejectReason;
    }

    public void setRejectReason(String rejectReason) {
        this.rejectReason = rejectReason;
    }

    public Integer getRejectCount() {
        return rejectCount;
    }

    public void setRejectCount(Integer rejectCount) {
        this.rejectCount = rejectCount;
    }

    public String getVisitNo() {
        return visitNo;
    }

    public void setVisitNo(String visitNo) {
        this.visitNo = visitNo;
    }

    public LocalDateTime getReceiveTime() {
        return receiveTime;
    }

    public void setReceiveTime(LocalDateTime receiveTime) {
        this.receiveTime = receiveTime;
    }

    public LocalDateTime getTestStartTime() {
        return testStartTime;
    }

    public void setTestStartTime(LocalDateTime testStartTime) {
        this.testStartTime = testStartTime;
    }

    public LocalDateTime getReportTime() {
        return reportTime;
    }

    public void setReportTime(LocalDateTime reportTime) {
        this.reportTime = reportTime;
    }

    public Long getOperatorId() {
        return operatorId;
    }

    public void setOperatorId(Long operatorId) {
        this.operatorId = operatorId;
    }

    public Long getCollectorId() {
        return collectorId;
    }

    public void setCollectorId(Long collectorId) {
        this.collectorId = collectorId;
    }

    public String getDepartment() {
        return department;
    }

    public void setDepartment(String department) {
        this.department = department;
    }

    public String getRemark() {
        return remark;
    }

    public void setRemark(String remark) {
        this.remark = remark;
    }

    public Integer getDeleted() {
        return deleted;
    }

    public void setDeleted(Integer deleted) {
        this.deleted = deleted;
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

}
