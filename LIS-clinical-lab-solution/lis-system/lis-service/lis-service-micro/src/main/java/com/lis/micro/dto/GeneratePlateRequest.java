package com.lis.micro.dto;

import java.util.List;

/**
 * 生成培养皿条码请求
 */
public class GeneratePlateRequest {

    /** 标本条码 (采集工作站生成) */
    private String sampleBarcode;

    /** 标本ID (可选) */
    private Long sampleId;

    /**
     * 标本类型 (医生下达申请的标本类型, 如 痰/尿/血等)
     * 用于匹配培养方案; 为空时仅匹配"适用于全部标本类型"的方案
     */
    private String sampleType;

    /** 标本对应的检验项目ID列表 */
    private List<Long> testItemIds;

    /**
     * 前端选中的培养方案ID (优先按此方案生成)
     * 传空时保留按检验项目ID匹配方案的兼容逻辑
     */
    private Long planId;

    /** 患者姓名 */
    private String patientName;

    /** 操作人ID */
    private Long operatorId;

    /** 操作人姓名 */
    private String operatorName;

    public String getSampleBarcode() {
        return sampleBarcode;
    }

    public void setSampleBarcode(String sampleBarcode) {
        this.sampleBarcode = sampleBarcode;
    }

    public Long getSampleId() {
        return sampleId;
    }

    public void setSampleId(Long sampleId) {
        this.sampleId = sampleId;
    }

    public String getSampleType() {
        return sampleType;
    }

    public void setSampleType(String sampleType) {
        this.sampleType = sampleType;
    }

    public List<Long> getTestItemIds() {
        return testItemIds;
    }

    public void setTestItemIds(List<Long> testItemIds) {
        this.testItemIds = testItemIds;
    }

    public Long getPlanId() {
        return planId;
    }

    public void setPlanId(Long planId) {
        this.planId = planId;
    }

    public String getPatientName() {
        return patientName;
    }

    public void setPatientName(String patientName) {
        this.patientName = patientName;
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
}