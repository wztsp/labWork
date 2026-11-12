package com.lis.report.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.LocalDateTime;

/**
 * 检验结果实体
 */
@TableName("lis_result")
public class Result {

    /** 审核状态: 未审核 */
    public static final int AUDIT_NONE = 0;
    /** 审核状态: 自动审核 */
    public static final int AUDIT_AUTO = 1;
    /** 审核状态: 人工审核 */
    public static final int AUDIT_MANUAL = 2;

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long sampleId;

    private Long testItemId;

    private String resultValue;

    private String resultText;

    private String unit;

    private String refRange;

    /** 异常标志: N=正常 H=偏高 L=偏低 */
    private String abnormalFlag;

    /** 是否危急值: 0=否 1=是 */
    private Integer isCritical;

    private Long instrumentId;

    private Long operatorId;

    private LocalDateTime testTime;

    /** 试剂批次ID (试剂追溯) */
    private Long reagentLotId;

    /** 试剂批号 (试剂追溯) */
    private String reagentLotNo;

    /** 上次结果值 (患者同一指标的上一次检验结果, 用于比对) */
    private String lastResultValue;

    /** 上次结果时间 */
    private LocalDateTime lastResultTime;

    /** 上次结果异常标志: N正常 H高 L低 */
    private String lastAbnormalFlag;

    /** OD值 (光密度, 仪器原始输出) */
    private java.math.BigDecimal odValue;

    /** CUTOFF值 (仪器计算阈值) */
    private java.math.BigDecimal cutoffValue;

    /** S/CO值 (样本信号 / cutoff 比值) */
    private java.math.BigDecimal scRatio;

    /** 自动审核标志: 0未做 1通过 2异常 */
    private Integer autoAudit;

    /** 自动审核异常信息 */
    private String autoAuditMessage;

    /** 项目知识 / 临床提示 */
    private String itemKnowledge;

    /** 检验结果图像 (JSON数组字符串, 血常规/尿常规等形态图像URL, 通常3-4张) */
    private String imageUrl;

    /** 审核状态: 0=未审核 1=自动审核 2=人工审核 */
    private Integer auditStatus;

    private Long auditorId;

    private LocalDateTime auditTime;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getSampleId() {
        return sampleId;
    }

    public void setSampleId(Long sampleId) {
        this.sampleId = sampleId;
    }

    public Long getTestItemId() {
        return testItemId;
    }

    public void setTestItemId(Long testItemId) {
        this.testItemId = testItemId;
    }

    public String getResultValue() {
        return resultValue;
    }

    public void setResultValue(String resultValue) {
        this.resultValue = resultValue;
    }

    public String getResultText() {
        return resultText;
    }

    public void setResultText(String resultText) {
        this.resultText = resultText;
    }

    public String getUnit() {
        return unit;
    }

    public void setUnit(String unit) {
        this.unit = unit;
    }

    public String getRefRange() {
        return refRange;
    }

    public void setRefRange(String refRange) {
        this.refRange = refRange;
    }

    public String getAbnormalFlag() {
        return abnormalFlag;
    }

    public void setAbnormalFlag(String abnormalFlag) {
        this.abnormalFlag = abnormalFlag;
    }

    public Integer getIsCritical() {
        return isCritical;
    }

    public void setIsCritical(Integer isCritical) {
        this.isCritical = isCritical;
    }

    public Long getInstrumentId() {
        return instrumentId;
    }

    public void setInstrumentId(Long instrumentId) {
        this.instrumentId = instrumentId;
    }

    public Long getOperatorId() {
        return operatorId;
    }

    public void setOperatorId(Long operatorId) {
        this.operatorId = operatorId;
    }

    public LocalDateTime getTestTime() {
        return testTime;
    }

    public void setTestTime(LocalDateTime testTime) {
        this.testTime = testTime;
    }

    public Long getReagentLotId() {
        return reagentLotId;
    }

    public void setReagentLotId(Long reagentLotId) {
        this.reagentLotId = reagentLotId;
    }

    public String getReagentLotNo() {
        return reagentLotNo;
    }

    public void setReagentLotNo(String reagentLotNo) {
        this.reagentLotNo = reagentLotNo;
    }

    public String getLastResultValue() {
        return lastResultValue;
    }

    public void setLastResultValue(String lastResultValue) {
        this.lastResultValue = lastResultValue;
    }

    public LocalDateTime getLastResultTime() {
        return lastResultTime;
    }

    public void setLastResultTime(LocalDateTime lastResultTime) {
        this.lastResultTime = lastResultTime;
    }

    public String getLastAbnormalFlag() {
        return lastAbnormalFlag;
    }

    public void setLastAbnormalFlag(String lastAbnormalFlag) {
        this.lastAbnormalFlag = lastAbnormalFlag;
    }

    public java.math.BigDecimal getOdValue() {
        return odValue;
    }

    public void setOdValue(java.math.BigDecimal odValue) {
        this.odValue = odValue;
    }

    public java.math.BigDecimal getCutoffValue() {
        return cutoffValue;
    }

    public void setCutoffValue(java.math.BigDecimal cutoffValue) {
        this.cutoffValue = cutoffValue;
    }

    public java.math.BigDecimal getScRatio() {
        return scRatio;
    }

    public void setScRatio(java.math.BigDecimal scRatio) {
        this.scRatio = scRatio;
    }

    public Integer getAutoAudit() {
        return autoAudit;
    }

    public void setAutoAudit(Integer autoAudit) {
        this.autoAudit = autoAudit;
    }

    public String getAutoAuditMessage() {
        return autoAuditMessage;
    }

    public void setAutoAuditMessage(String autoAuditMessage) {
        this.autoAuditMessage = autoAuditMessage;
    }

    public String getItemKnowledge() {
        return itemKnowledge;
    }

    public void setItemKnowledge(String itemKnowledge) {
        this.itemKnowledge = itemKnowledge;
    }

    public String getImageUrl() {
        return imageUrl;
    }

    public void setImageUrl(String imageUrl) {
        this.imageUrl = imageUrl;
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

    public LocalDateTime getAuditTime() {
        return auditTime;
    }

    public void setAuditTime(LocalDateTime auditTime) {
        this.auditTime = auditTime;
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
