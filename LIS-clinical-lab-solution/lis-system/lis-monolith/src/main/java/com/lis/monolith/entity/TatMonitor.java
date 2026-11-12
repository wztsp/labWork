package com.lis.monolith.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * TAT 超时监控记录
 */
@TableName("lis_tat_monitor")
public class TatMonitor implements Serializable {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long sampleId;

    private String sampleBarcode;

    private String patientName;

    private Long testItemId;

    private String testItemName;

    private String specimenType;

    /** 0否 1是 */
    private Integer isUrgent;

    private LocalDateTime signOffTime;

    /** 该规则时限(分钟) */
    private Integer timeoutMinutes;

    /** 已耗时(分钟) */
    private Integer elapsedMinutes;

    /** 0未超时 1已超时 */
    private Integer isOverdue;

    private Long operatorId;

    private String operatorName;

    private String department;

    /** 0未通知 1已通知 */
    private Integer notified;

    private LocalDateTime createTime;

    // ===== getters / setters =====
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getSampleId() { return sampleId; }
    public void setSampleId(Long sampleId) { this.sampleId = sampleId; }
    public String getSampleBarcode() { return sampleBarcode; }
    public void setSampleBarcode(String sampleBarcode) { this.sampleBarcode = sampleBarcode; }
    public String getPatientName() { return patientName; }
    public void setPatientName(String patientName) { this.patientName = patientName; }
    public Long getTestItemId() { return testItemId; }
    public void setTestItemId(Long testItemId) { this.testItemId = testItemId; }
    public String getTestItemName() { return testItemName; }
    public void setTestItemName(String testItemName) { this.testItemName = testItemName; }
    public String getSpecimenType() { return specimenType; }
    public void setSpecimenType(String specimenType) { this.specimenType = specimenType; }
    public Integer getIsUrgent() { return isUrgent; }
    public void setIsUrgent(Integer isUrgent) { this.isUrgent = isUrgent; }
    public LocalDateTime getSignOffTime() { return signOffTime; }
    public void setSignOffTime(LocalDateTime signOffTime) { this.signOffTime = signOffTime; }
    public Integer getTimeoutMinutes() { return timeoutMinutes; }
    public void setTimeoutMinutes(Integer timeoutMinutes) { this.timeoutMinutes = timeoutMinutes; }
    public Integer getElapsedMinutes() { return elapsedMinutes; }
    public void setElapsedMinutes(Integer elapsedMinutes) { this.elapsedMinutes = elapsedMinutes; }
    public Integer getIsOverdue() { return isOverdue; }
    public void setIsOverdue(Integer isOverdue) { this.isOverdue = isOverdue; }
    public Long getOperatorId() { return operatorId; }
    public void setOperatorId(Long operatorId) { this.operatorId = operatorId; }
    public String getOperatorName() { return operatorName; }
    public void setOperatorName(String operatorName) { this.operatorName = operatorName; }
    public String getDepartment() { return department; }
    public void setDepartment(String department) { this.department = department; }
    public Integer getNotified() { return notified; }
    public void setNotified(Integer notified) { this.notified = notified; }
    public LocalDateTime getCreateTime() { return createTime; }
    public void setCreateTime(LocalDateTime createTime) { this.createTime = createTime; }
}