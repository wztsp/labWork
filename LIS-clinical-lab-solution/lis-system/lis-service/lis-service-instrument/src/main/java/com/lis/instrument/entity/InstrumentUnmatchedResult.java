package com.lis.instrument.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 仪器上传但未匹配到业务标本的结果（无主标本）。
 *
 * <p>对应表 {@code lis_instrument_unmatched_result}。
 * 仪器上传数据经条码/样本号匹配不到 {@code lis_sample} 时落库，供技师工作站
 * 手工调整/认领后重新匹配到业务标本，避免数据丢失。</p>
 *
 * <p>{@code status}: 0待处理 1已匹配 2已作废</p>
 *
 * @author LIS Team
 */
@TableName("lis_instrument_unmatched_result")
public class InstrumentUnmatchedResult implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    /** 来源仪器 ID */
    private Long instrumentId;

    /** 来源仪器名称快照 */
    private String instrumentName;

    /** 仪器上传的样本号/条码（无主标本的核心标识） */
    private String sampleNo;

    /** 检验指标代码 */
    private String testCode;

    /** 检验指标名称快照（可空，匹配到指标后回填） */
    private String testName;

    /** 结果值 */
    private String resultValue;

    /** 单位 */
    private String unit;

    /** 参考范围 */
    private String refRange;

    /** 异常标志（如 H/L *** 等） */
    private String abnormalFlag;

    /** 是否危急值：0否 1是 */
    private Integer isCritical;

    /** 状态：0待处理 1已匹配 2已作废 */
    private Integer status;

    /** 匹配到的业务标本 ID（成功后回填） */
    private Long matchedSampleId;

    /** 转质控后回填：生成的质控记录 ID (lis_qc_record.id, 溯源用) */
    private Long matchedQcRecordId;

    /** 转质控后回填：使用的质控品批号 ID (lis_qc_lot.id) */
    private Long matchedQcLotId;

    /** 备注 / 处理说明 */
    private String remark;

    /** 仪器检测时间 */
    private LocalDateTime testTime;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;

    public InstrumentUnmatchedResult() {
    }

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

    public String getInstrumentName() {
        return instrumentName;
    }

    public void setInstrumentName(String instrumentName) {
        this.instrumentName = instrumentName;
    }

    public String getSampleNo() {
        return sampleNo;
    }

    public void setSampleNo(String sampleNo) {
        this.sampleNo = sampleNo;
    }

    public String getTestCode() {
        return testCode;
    }

    public void setTestCode(String testCode) {
        this.testCode = testCode;
    }

    public String getTestName() {
        return testName;
    }

    public void setTestName(String testName) {
        this.testName = testName;
    }

    public String getResultValue() {
        return resultValue;
    }

    public void setResultValue(String resultValue) {
        this.resultValue = resultValue;
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

    public Integer getStatus() {
        return status;
    }

    public void setStatus(Integer status) {
        this.status = status;
    }

    public Long getMatchedSampleId() {
        return matchedSampleId;
    }

    public void setMatchedSampleId(Long matchedSampleId) {
        this.matchedSampleId = matchedSampleId;
    }

    public Long getMatchedQcRecordId() {
        return matchedQcRecordId;
    }

    public void setMatchedQcRecordId(Long matchedQcRecordId) {
        this.matchedQcRecordId = matchedQcRecordId;
    }

    public Long getMatchedQcLotId() {
        return matchedQcLotId;
    }

    public void setMatchedQcLotId(Long matchedQcLotId) {
        this.matchedQcLotId = matchedQcLotId;
    }

    public String getRemark() {
        return remark;
    }

    public void setRemark(String remark) {
        this.remark = remark;
    }

    public LocalDateTime getTestTime() {
        return testTime;
    }

    public void setTestTime(LocalDateTime testTime) {
        this.testTime = testTime;
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