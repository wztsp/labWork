package com.lis.micro.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 血培养记录
 */
@TableName("lis_blood_culture")
public class BloodCulture {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long sampleId;
    private String bottleNo;
    private String mediumType;
    private Long instrumentId;
    private LocalDateTime inoculationTime;
    private LocalDateTime alarmTime;
    /** POSITIVE/NEGATIVE/CONTAMINATED */
    private String finalResult;
    private LocalDateTime finalTime;
    /** 报警时间 - 接种时间 (小时) */
    private BigDecimal ttpHours;
    private Long operatorId;
    /** 0培养中 1阳性 2阴性 3污染 */
    private Integer status;
    private LocalDateTime createTime;

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

    public String getBottleNo() {
        return bottleNo;
    }

    public void setBottleNo(String bottleNo) {
        this.bottleNo = bottleNo;
    }

    public String getMediumType() {
        return mediumType;
    }

    public void setMediumType(String mediumType) {
        this.mediumType = mediumType;
    }

    public Long getInstrumentId() {
        return instrumentId;
    }

    public void setInstrumentId(Long instrumentId) {
        this.instrumentId = instrumentId;
    }

    public LocalDateTime getInoculationTime() {
        return inoculationTime;
    }

    public void setInoculationTime(LocalDateTime inoculationTime) {
        this.inoculationTime = inoculationTime;
    }

    public LocalDateTime getAlarmTime() {
        return alarmTime;
    }

    public void setAlarmTime(LocalDateTime alarmTime) {
        this.alarmTime = alarmTime;
    }

    public String getFinalResult() {
        return finalResult;
    }

    public void setFinalResult(String finalResult) {
        this.finalResult = finalResult;
    }

    public LocalDateTime getFinalTime() {
        return finalTime;
    }

    public void setFinalTime(LocalDateTime finalTime) {
        this.finalTime = finalTime;
    }

    public BigDecimal getTtpHours() {
        return ttpHours;
    }

    public void setTtpHours(BigDecimal ttpHours) {
        this.ttpHours = ttpHours;
    }

    public Long getOperatorId() {
        return operatorId;
    }

    public void setOperatorId(Long operatorId) {
        this.operatorId = operatorId;
    }

    public Integer getStatus() {
        return status;
    }

    public void setStatus(Integer status) {
        this.status = status;
    }

    public LocalDateTime getCreateTime() {
        return createTime;
    }

    public void setCreateTime(LocalDateTime createTime) {
        this.createTime = createTime;
    }

}
