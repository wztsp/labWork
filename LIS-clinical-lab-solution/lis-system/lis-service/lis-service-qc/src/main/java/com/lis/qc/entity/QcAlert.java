package com.lis.qc.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 质控偏差趋势预警实体
 */
@TableName("lis_qc_alert")
public class QcAlert {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 预警类型: SAME_SIDE_BIAS/TREND/TREND_AND_SAME_SIDE */
    private String alertType;

    private Long lotId;

    private Long testItemId;

    private Long instrumentId;

    private BigDecimal lastMeasureValue;

    private BigDecimal lastZScore;

    private String message;

    /** PENDING 待处理 / READ 已读 */
    private String status;

    private LocalDateTime alertTime;

    private LocalDateTime createTime;

    // ===== 非数据库字段, 用于前端展示 =====

    @TableField(exist = false)
    private String testItemName;

    @TableField(exist = false)
    private String instrumentName;

    @TableField(exist = false)
    private String lotNo;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getAlertType() {
        return alertType;
    }

    public void setAlertType(String alertType) {
        this.alertType = alertType;
    }

    public Long getLotId() {
        return lotId;
    }

    public void setLotId(Long lotId) {
        this.lotId = lotId;
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

    public BigDecimal getLastMeasureValue() {
        return lastMeasureValue;
    }

    public void setLastMeasureValue(BigDecimal lastMeasureValue) {
        this.lastMeasureValue = lastMeasureValue;
    }

    public BigDecimal getLastZScore() {
        return lastZScore;
    }

    public void setLastZScore(BigDecimal lastZScore) {
        this.lastZScore = lastZScore;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public LocalDateTime getAlertTime() {
        return alertTime;
    }

    public void setAlertTime(LocalDateTime alertTime) {
        this.alertTime = alertTime;
    }

    public LocalDateTime getCreateTime() {
        return createTime;
    }

    public void setCreateTime(LocalDateTime createTime) {
        this.createTime = createTime;
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
