package com.lis.qc.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 质控记录实体
 */
@TableName("lis_qc_record")
public class QCRecord {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long qcLotId;

    private Long testItemId;

    private Long instrumentId;

    /** 实测值 */
    private BigDecimal measureValue;

    private LocalDateTime measureTime;

    private Long operatorId;

    /** Z 分数 = (实测值 - 靶值) / SD */
    private BigDecimal zScore;

    /** 触发的 Westgard 规则 (逗号分隔，如 "1-3s,2-2s") */
    private String ruleViolation;

    /** 质控结果: ACCEPT=接受 REJECT=拒绝 */
    private String qcResult;

    /** 来源标本ID (技师工作站手工标本转质控时记录, 用于取消质控时定位) */
    private Long sampleId;

    private LocalDateTime createTime;

    /** 逻辑删除: 0=未删除 1=已删除 */
    @TableLogic
    private Integer deleted;

    // ===== 非数据库字段, 用于前端展示 =====

    /** 检验指标名称 (关联lis_test_item) */
    @TableField(exist = false)
    private String testItemName;

    /** 仪器名称 (关联lis_instrument) */
    @TableField(exist = false)
    private String instrumentName;

    /** 批号 (关联lis_qc_lot) */
    @TableField(exist = false)
    private String lotNo;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getQcLotId() {
        return qcLotId;
    }

    public void setQcLotId(Long qcLotId) {
        this.qcLotId = qcLotId;
    }

    public Long getTestItemId() {
        return testItemId;
    }

    public void setTestItemId(Long testItemId) {
        this.testItemId = testItemId;
    }

    public Long getInstrumentId() {
        return instrumentId;
    }

    public void setInstrumentId(Long instrumentId) {
        this.instrumentId = instrumentId;
    }

    public BigDecimal getMeasureValue() {
        return measureValue;
    }

    public void setMeasureValue(BigDecimal measureValue) {
        this.measureValue = measureValue;
    }

    public LocalDateTime getMeasureTime() {
        return measureTime;
    }

    public void setMeasureTime(LocalDateTime measureTime) {
        this.measureTime = measureTime;
    }

    public Long getOperatorId() {
        return operatorId;
    }

    public void setOperatorId(Long operatorId) {
        this.operatorId = operatorId;
    }

    public BigDecimal getZScore() {
        return zScore;
    }

    public void setZScore(BigDecimal zScore) {
        this.zScore = zScore;
    }

    public String getRuleViolation() {
        return ruleViolation;
    }

    public void setRuleViolation(String ruleViolation) {
        this.ruleViolation = ruleViolation;
    }

    public String getQcResult() {
        return qcResult;
    }

    public void setQcResult(String qcResult) {
        this.qcResult = qcResult;
    }

    public Long getSampleId() {
        return sampleId;
    }

    public void setSampleId(Long sampleId) {
        this.sampleId = sampleId;
    }

    public LocalDateTime getCreateTime() {
        return createTime;
    }

    public void setCreateTime(LocalDateTime createTime) {
        this.createTime = createTime;
    }

    public Integer getDeleted() {
        return deleted;
    }

    public void setDeleted(Integer deleted) {
        this.deleted = deleted;
    }

    public String getTestItemName() {
        return testItemName;
    }

    public void setTestItemName(String testItemName) {
        this.testItemName = testItemName;
    }

    public String getInstrumentName() {
        return instrumentName;
    }

    public void setInstrumentName(String instrumentName) {
        this.instrumentName = instrumentName;
    }

    public String getLotNo() {
        return lotNo;
    }

    public void setLotNo(String lotNo) {
        this.lotNo = lotNo;
    }

}
