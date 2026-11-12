package com.lis.monolith.vo;

/**
 * 传染病扫描行 (结果 x 传染病配置)
 */
public class InfectiousScanRow {

    private Long resultId;
    private Long sampleId;
    private Long testItemId;
    private String resultValue;
    private String resultText;
    private String abnormalFlag;
    private Integer auditStatus;
    private String testItemName;
    private String positiveText;
    private String patientName;
    private String sampleBarcode;
    private String department;

    public Long getResultId() { return resultId; }
    public void setResultId(Long resultId) { this.resultId = resultId; }
    public Long getSampleId() { return sampleId; }
    public void setSampleId(Long sampleId) { this.sampleId = sampleId; }
    public Long getTestItemId() { return testItemId; }
    public void setTestItemId(Long testItemId) { this.testItemId = testItemId; }
    public String getResultValue() { return resultValue; }
    public void setResultValue(String resultValue) { this.resultValue = resultValue; }
    public String getResultText() { return resultText; }
    public void setResultText(String resultText) { this.resultText = resultText; }
    public String getAbnormalFlag() { return abnormalFlag; }
    public void setAbnormalFlag(String abnormalFlag) { this.abnormalFlag = abnormalFlag; }
    public Integer getAuditStatus() { return auditStatus; }
    public void setAuditStatus(Integer auditStatus) { this.auditStatus = auditStatus; }
    public String getTestItemName() { return testItemName; }
    public void setTestItemName(String testItemName) { this.testItemName = testItemName; }
    public String getPositiveText() { return positiveText; }
    public void setPositiveText(String positiveText) { this.positiveText = positiveText; }
    public String getPatientName() { return patientName; }
    public void setPatientName(String patientName) { this.patientName = patientName; }
    public String getSampleBarcode() { return sampleBarcode; }
    public void setSampleBarcode(String sampleBarcode) { this.sampleBarcode = sampleBarcode; }
    public String getDepartment() { return department; }
    public void setDepartment(String department) { this.department = department; }
}