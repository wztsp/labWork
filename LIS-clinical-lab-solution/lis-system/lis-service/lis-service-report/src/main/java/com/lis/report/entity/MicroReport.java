package com.lis.report.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.LocalDateTime;

/**
 * 微生物报告实体
 */
@TableName("lis_micro_report")
public class MicroReport {

    /** 报告状态: 初步报告 */
    public static final int STATUS_PRELIMINARY = 1;
    /** 报告状态: 鉴定报告 */
    public static final int STATUS_IDENTIFICATION = 2;
    /** 报告状态: 最终报告 */
    public static final int STATUS_FINAL = 3;

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    /** 报告编号 */
    private String reportNo;

    /** 标本ID */
    private Long sampleId;

    /** 标本条码(冗余存一份,便于展示和查询) */
    private String sampleBarcode;

    /** 培养条码(培养基条码). 一条微生物报告由一次培养基条码核收产生, 同一采集条码可能对应多条培养条码, 每条独立出报告. */
    private String plateBarcode;

    /** 患者ID */
    private Long patientId;

    /** 患者姓名 */
    private String patientName;

    /** 标本类型 */
    private String specimenType;

    /** 培养部位 */
    private String cultureSite;

    /** 报告状态: 1=初步报告 2=鉴定报告 3=最终报告 */
    private Integer reportStatus;

    /** 审核人ID */
    private Long auditorId;

    /** 审核时间 */
    private LocalDateTime auditTime;

    /** 发布时间 */
    private LocalDateTime publishTime;

    // ============ 革兰氏染色 ============
    /** 革兰氏染色结果 */
    private String gramStainResult;

    /** 革兰氏染色备注 */
    private String gramStainRemark;

    /** 革兰氏染色时间 */
    private LocalDateTime gramStainTime;

    /** 革兰氏染色操作人 */
    private String gramStainOperator;

    // ============ 阴性报告 ============
    /** 是否阴性报告: 0=否 1=是 */
    private Integer isNegative;

    /** 阴性报告备注 */
    private String negativeRemark;

    /** 阴性报告操作人 */
    private String negativeOperator;

    /** 阴性报告时间 */
    private LocalDateTime negativeTime;

    // ============ 血培养专用 ============
    /** 培养瓶号 */
    private String bottleNo;
    /** 瓶类型: 成人瓶/儿童瓶/厌氧瓶/需氧瓶 */
    private String bottleType;
    /** 血量 */
    private String volume;
    /** 接种时间 */
    private LocalDateTime inoculateTime;
    /** 阳性报警时间 */
    private LocalDateTime alarmTime;
    /** TTP小时数(报警-接种) */
    private java.math.BigDecimal ttpHours;
    /** 关联血培养仪记录ID */
    private Long bloodCultureId;
    /** 血培养结果: POSITIVE/NEGATIVE/PENDING */
    private String bloodCultureResult;

    /** 创建时间 */
    private LocalDateTime createTime;

    /** 更新时间 */
    private LocalDateTime updateTime;

    /**
     * 报告状态文本
     */
    public static String statusText(Integer status) {
        if (status == null) return "未知";
        return switch (status) {
            case STATUS_PRELIMINARY -> "初步报告";
            case STATUS_IDENTIFICATION -> "鉴定报告";
            case STATUS_FINAL -> "最终报告";
            default -> "未知";
        };
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getReportNo() {
        return reportNo;
    }

    public void setReportNo(String reportNo) {
        this.reportNo = reportNo;
    }

    public Long getSampleId() {
        return sampleId;
    }

    public void setSampleId(Long sampleId) {
        this.sampleId = sampleId;
    }

    public String getSampleBarcode() {
        return sampleBarcode;
    }

    public void setSampleBarcode(String sampleBarcode) {
        this.sampleBarcode = sampleBarcode;
    }

    public String getPlateBarcode() {
        return plateBarcode;
    }

    public void setPlateBarcode(String plateBarcode) {
        this.plateBarcode = plateBarcode;
    }

    public Long getPatientId() {
        return patientId;
    }

    public void setPatientId(Long patientId) {
        this.patientId = patientId;
    }

    public String getPatientName() {
        return patientName;
    }

    public void setPatientName(String patientName) {
        this.patientName = patientName;
    }

    public String getSpecimenType() {
        return specimenType;
    }

    public void setSpecimenType(String specimenType) {
        this.specimenType = specimenType;
    }

    public String getCultureSite() {
        return cultureSite;
    }

    public void setCultureSite(String cultureSite) {
        this.cultureSite = cultureSite;
    }

    public Integer getReportStatus() {
        return reportStatus;
    }

    public void setReportStatus(Integer reportStatus) {
        this.reportStatus = reportStatus;
    }

    public Long getAuditorId() {
        return auditorId;
    }

    public void setAuditorId(Long auditorId) {
        this.auditorId = auditorId;
    }

    public LocalDateTime getAuditTime() {
        return auditTime;
    }

    public void setAuditTime(LocalDateTime auditTime) {
        this.auditTime = auditTime;
    }

    public LocalDateTime getPublishTime() {
        return publishTime;
    }

    public void setPublishTime(LocalDateTime publishTime) {
        this.publishTime = publishTime;
    }

    public String getGramStainResult() {
        return gramStainResult;
    }

    public void setGramStainResult(String gramStainResult) {
        this.gramStainResult = gramStainResult;
    }

    public String getGramStainRemark() {
        return gramStainRemark;
    }

    public void setGramStainRemark(String gramStainRemark) {
        this.gramStainRemark = gramStainRemark;
    }

    public LocalDateTime getGramStainTime() {
        return gramStainTime;
    }

    public void setGramStainTime(LocalDateTime gramStainTime) {
        this.gramStainTime = gramStainTime;
    }

    public String getGramStainOperator() {
        return gramStainOperator;
    }

    public void setGramStainOperator(String gramStainOperator) {
        this.gramStainOperator = gramStainOperator;
    }

    public Integer getIsNegative() {
        return isNegative;
    }

    public void setIsNegative(Integer isNegative) {
        this.isNegative = isNegative;
    }

    public String getNegativeRemark() {
        return negativeRemark;
    }

    public void setNegativeRemark(String negativeRemark) {
        this.negativeRemark = negativeRemark;
    }

    public String getNegativeOperator() {
        return negativeOperator;
    }

    public void setNegativeOperator(String negativeOperator) {
        this.negativeOperator = negativeOperator;
    }

    public LocalDateTime getNegativeTime() {
        return negativeTime;
    }

    public void setNegativeTime(LocalDateTime negativeTime) {
        this.negativeTime = negativeTime;
    }

    public String getBottleNo() {
        return bottleNo;
    }

    public void setBottleNo(String bottleNo) {
        this.bottleNo = bottleNo;
    }

    public String getBottleType() {
        return bottleType;
    }

    public void setBottleType(String bottleType) {
        this.bottleType = bottleType;
    }

    public String getVolume() {
        return volume;
    }

    public void setVolume(String volume) {
        this.volume = volume;
    }

    public LocalDateTime getInoculateTime() {
        return inoculateTime;
    }

    public void setInoculateTime(LocalDateTime inoculateTime) {
        this.inoculateTime = inoculateTime;
    }

    public LocalDateTime getAlarmTime() {
        return alarmTime;
    }

    public void setAlarmTime(LocalDateTime alarmTime) {
        this.alarmTime = alarmTime;
    }

    public Long getBloodCultureId() {
        return bloodCultureId;
    }

    public void setBloodCultureId(Long bloodCultureId) {
        this.bloodCultureId = bloodCultureId;
    }

    public String getBloodCultureResult() {
        return bloodCultureResult;
    }

    public void setBloodCultureResult(String bloodCultureResult) {
        this.bloodCultureResult = bloodCultureResult;
    }

    public java.math.BigDecimal getTtpHours() {
        return ttpHours;
    }

    public void setTtpHours(java.math.BigDecimal ttpHours) {
        this.ttpHours = ttpHours;
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
