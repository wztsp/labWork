package com.lis.qc.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 室间质控 (EQA) 记录实体
 */
@TableName("lis_eqa_record")
public class EqaRecord {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** EQA 批号 */
    private String eqaBatchNo;

    /** 检验项目 ID */
    private Long testItemId;

    /** 检验项目名称 (室间质控允许直接记录名称) */
    private String testItemName;

    /** 仪器 ID */
    private Long instrumentId;

    /** 仪器名称 */
    private String instrumentName;

    /** 靶值 (组织者提供) */
    private BigDecimal targetValue;

    /** 实测值 */
    private BigDecimal measureValue;

    /** 标准差 */
    private BigDecimal sd;

    /** 变异系数 */
    private BigDecimal cv;

    /** 得分 (0-100) */
    private BigDecimal score;

    /** 等级: A/B/C/D */
    private String grade;

    /** 是否合格 */
    private Boolean passed;

    private LocalDateTime measureTime;

    private LocalDateTime resultTime;

    private LocalDateTime createTime;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getEqaBatchNo() {
        return eqaBatchNo;
    }

    public void setEqaBatchNo(String eqaBatchNo) {
        this.eqaBatchNo = eqaBatchNo;
    }

    public Long getTestItemId() {
        return testItemId;
    }

    public void setTestItemId(Long testItemId) {
        this.testItemId = testItemId;
    }

    public String getTestItemName() {
        return testItemName;
    }

    public void setTestItemName(String testItemName) {
        this.testItemName = testItemName;
    }

    public Long getInstrumentId() {
        return instrumentId;
    }

    public void setInstrumentId(Long instrumentId) {
        this.instrumentId = instrumentId;
    }

    public String getInstrumentName() {
        return instrumentName;
    }

    public void setInstrumentName(String instrumentName) {
        this.instrumentName = instrumentName;
    }

    public BigDecimal getTargetValue() {
        return targetValue;
    }

    public void setTargetValue(BigDecimal targetValue) {
        this.targetValue = targetValue;
    }

    public BigDecimal getMeasureValue() {
        return measureValue;
    }

    public void setMeasureValue(BigDecimal measureValue) {
        this.measureValue = measureValue;
    }

    public BigDecimal getSd() {
        return sd;
    }

    public void setSd(BigDecimal sd) {
        this.sd = sd;
    }

    public BigDecimal getCv() {
        return cv;
    }

    public void setCv(BigDecimal cv) {
        this.cv = cv;
    }

    public BigDecimal getScore() {
        return score;
    }

    public void setScore(BigDecimal score) {
        this.score = score;
    }

    public String getGrade() {
        return grade;
    }

    public void setGrade(String grade) {
        this.grade = grade;
    }

    public Boolean getPassed() {
        return passed;
    }

    public void setPassed(Boolean passed) {
        this.passed = passed;
    }

    public LocalDateTime getMeasureTime() {
        return measureTime;
    }

    public void setMeasureTime(LocalDateTime measureTime) {
        this.measureTime = measureTime;
    }

    public LocalDateTime getResultTime() {
        return resultTime;
    }

    public void setResultTime(LocalDateTime resultTime) {
        this.resultTime = resultTime;
    }

    public LocalDateTime getCreateTime() {
        return createTime;
    }

    public void setCreateTime(LocalDateTime createTime) {
        this.createTime = createTime;
    }

}
