package com.lis.base.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 仪器校准记录
 */
@TableName("lis_instrument_calibration")
public class InstrumentCalibration implements Serializable {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    /** 仪器ID */
    private Long instrumentId;

    /** 校准编号 */
    private String calibrationNo;

    /** 校准类型 */
    private String calibrationType;

    /** 校准时间 */
    private LocalDateTime calibrationTime;

    /** 下次校准时间 */
    private LocalDateTime nextCalibrationTime;

    /** 操作人ID */
    private Long operatorId;

    /** 操作人姓名 */
    private String operatorName;

    /** 校准结果 */
    private String result;

    /** 备注 */
    private String remark;

    /** 创建时间 */
    private LocalDateTime createTime;

    /** 更新时间 */
    private LocalDateTime updateTime;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getInstrumentId() {
        return instrumentId;
    }

    public void setInstrumentId(Long instrumentId) {
        this.instrumentId = instrumentId;
    }

    public String getCalibrationNo() {
        return calibrationNo;
    }

    public void setCalibrationNo(String calibrationNo) {
        this.calibrationNo = calibrationNo;
    }

    public String getCalibrationType() {
        return calibrationType;
    }

    public void setCalibrationType(String calibrationType) {
        this.calibrationType = calibrationType;
    }

    public LocalDateTime getCalibrationTime() {
        return calibrationTime;
    }

    public void setCalibrationTime(LocalDateTime calibrationTime) {
        this.calibrationTime = calibrationTime;
    }

    public LocalDateTime getNextCalibrationTime() {
        return nextCalibrationTime;
    }

    public void setNextCalibrationTime(LocalDateTime nextCalibrationTime) {
        this.nextCalibrationTime = nextCalibrationTime;
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

    public String getResult() {
        return result;
    }

    public void setResult(String result) {
        this.result = result;
    }

    public String getRemark() {
        return remark;
    }

    public void setRemark(String remark) {
        this.remark = remark;
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
