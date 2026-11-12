package com.lis.monolith.vo;

import java.time.LocalDateTime;

/**
 * TAT 扫描行 (标本 x 检验项目 x 规则)
 */
public class TatScanRow {

    private Long sampleId;
    private String sampleBarcode;
    private String patientName;
    private String specimenType;
    private Integer isUrgent;
    private LocalDateTime signOffTime;
    private Long operatorId;
    private String department;
    private Long testItemId;
    private String testItemName;
    private Integer urgentTimeout;
    private Integer normalTimeout;

    public Long getSampleId() { return sampleId; }
    public void setSampleId(Long sampleId) { this.sampleId = sampleId; }
    public String getSampleBarcode() { return sampleBarcode; }
    public void setSampleBarcode(String sampleBarcode) { this.sampleBarcode = sampleBarcode; }
    public String getPatientName() { return patientName; }
    public void setPatientName(String patientName) { this.patientName = patientName; }
    public String getSpecimenType() { return specimenType; }
    public void setSpecimenType(String specimenType) { this.specimenType = specimenType; }
    public Integer getIsUrgent() { return isUrgent; }
    public void setIsUrgent(Integer isUrgent) { this.isUrgent = isUrgent; }
    public LocalDateTime getSignOffTime() { return signOffTime; }
    public void setSignOffTime(LocalDateTime signOffTime) { this.signOffTime = signOffTime; }
    public Long getOperatorId() { return operatorId; }
    public void setOperatorId(Long operatorId) { this.operatorId = operatorId; }
    public String getDepartment() { return department; }
    public void setDepartment(String department) { this.department = department; }
    public Long getTestItemId() { return testItemId; }
    public void setTestItemId(Long testItemId) { this.testItemId = testItemId; }
    public String getTestItemName() { return testItemName; }
    public void setTestItemName(String testItemName) { this.testItemName = testItemName; }
    public Integer getUrgentTimeout() { return urgentTimeout; }
    public void setUrgentTimeout(Integer urgentTimeout) { this.urgentTimeout = urgentTimeout; }
    public Integer getNormalTimeout() { return normalTimeout; }
    public void setNormalTimeout(Integer normalTimeout) { this.normalTimeout = normalTimeout; }
}