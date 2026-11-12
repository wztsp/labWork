package com.lis.micro.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 标本培养皿条码
 * 一个标本(采集工作站条码) 按培养方案生成多个培养皿独立条码
 */
@TableName("lis_sample_plate")
public class SamplePlate implements Serializable {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 标本ID */
    private Long sampleId;

    /** 标本条码 (采集工作站生成) */
    private String sampleBarcode;

    /** 培养皿条码 (独立新编号) */
    private String plateBarcode;

    /** 培养方案ID */
    private Long planId;

    /** 培养基类型ID */
    private Long mediumTypeId;

    /** 培养基名称 (冗余) */
    private String mediumTypeName;

    /** 患者姓名 (冗余, 打印标签用) */
    private String patientName;

    /** 序号 (同一标本下递增) */
    private Integer seqNo;

    /** 状态: 0未核收 1已核收 2已作废(取消) */
    private Integer status;

    /** 操作人ID */
    private Long operatorId;

    /** 操作人姓名 (冗余) */
    private String operatorName;

    private LocalDateTime createTime;

    /** 取消时间 (作废时记录) */
    private LocalDateTime cancelTime;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getSampleId() { return sampleId; }
    public void setSampleId(Long sampleId) { this.sampleId = sampleId; }
    public String getSampleBarcode() { return sampleBarcode; }
    public void setSampleBarcode(String sampleBarcode) { this.sampleBarcode = sampleBarcode; }
    public String getPlateBarcode() { return plateBarcode; }
    public void setPlateBarcode(String plateBarcode) { this.plateBarcode = plateBarcode; }
    public Long getPlanId() { return planId; }
    public void setPlanId(Long planId) { this.planId = planId; }
    public Long getMediumTypeId() { return mediumTypeId; }
    public void setMediumTypeId(Long mediumTypeId) { this.mediumTypeId = mediumTypeId; }
    public String getMediumTypeName() { return mediumTypeName; }
    public void setMediumTypeName(String mediumTypeName) { this.mediumTypeName = mediumTypeName; }
    public String getPatientName() { return patientName; }
    public void setPatientName(String patientName) { this.patientName = patientName; }
    public Integer getSeqNo() { return seqNo; }
    public void setSeqNo(Integer seqNo) { this.seqNo = seqNo; }
    public Integer getStatus() { return status; }
    public void setStatus(Integer status) { this.status = status; }
    public Long getOperatorId() { return operatorId; }
    public void setOperatorId(Long operatorId) { this.operatorId = operatorId; }
    public String getOperatorName() { return operatorName; }
    public void setOperatorName(String operatorName) { this.operatorName = operatorName; }
    public LocalDateTime getCreateTime() { return createTime; }
    public void setCreateTime(LocalDateTime createTime) { this.createTime = createTime; }
    public LocalDateTime getCancelTime() { return cancelTime; }
    public void setCancelTime(LocalDateTime cancelTime) { this.cancelTime = cancelTime; }
}