package com.lis.sample.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 检验申请
 */
@TableName("lis_lab_order")
public class LabOrder implements Serializable {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    /** 申请单号 */
    private String orderNo;

    /** 就诊ID */
    private Long visitId;

    /** 患者ID */
    private Long patientId;

    /** 申请类型: 1门诊 2住院 3急诊 */
    private Integer orderType;

    /** 状态: 0待接收 1已接收 2已取消 3已完成 */
    private Integer status;

    /** 申请医生 */
    private String applyDoctor;

    /** 申请科室 */
    private String applyDept;

    /** 临床诊断 */
    private String clinicalDiag;

    /** 患者生理条件: 如 女性-卵泡期/立位/晨8点/空腹/孕妇-孕早期 等, 用于匹配参考范围 */
    private String patientCondition;

    /** 申请时间 */
    private LocalDateTime applyTime;

    /** 接收时间 */
    private LocalDateTime receiveTime;

    /** 取消时间 */
    private LocalDateTime cancelTime;

    /** HIS 同步状态: 0未同步 1已同步 */
    private Integer hisSyncStatus;

    /** 采集方式ID (关联 lis_collection_method.id) */
    private Long collectionMethodId;

    /** 采集方式名称 (冗余, 便于直接展示) */
    private String collectionMethodName;

    /** 创建时间 */
    private LocalDateTime createTime;

    /** HIS 申请唯一键(申请号#项目序号), 用于接口重复推送时幂等去重 */
    private String hisApplyId;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getOrderNo() {
        return orderNo;
    }

    public void setOrderNo(String orderNo) {
        this.orderNo = orderNo;
    }

    public Long getVisitId() {
        return visitId;
    }

    public void setVisitId(Long visitId) {
        this.visitId = visitId;
    }

    public Long getPatientId() {
        return patientId;
    }

    public void setPatientId(Long patientId) {
        this.patientId = patientId;
    }

    public Integer getOrderType() {
        return orderType;
    }

    public void setOrderType(Integer orderType) {
        this.orderType = orderType;
    }

    public Integer getStatus() {
        return status;
    }

    public void setStatus(Integer status) {
        this.status = status;
    }

    public String getApplyDoctor() {
        return applyDoctor;
    }

    public void setApplyDoctor(String applyDoctor) {
        this.applyDoctor = applyDoctor;
    }

    public String getApplyDept() {
        return applyDept;
    }

    public void setApplyDept(String applyDept) {
        this.applyDept = applyDept;
    }

    public String getClinicalDiag() {
        return clinicalDiag;
    }

    public void setClinicalDiag(String clinicalDiag) {
        this.clinicalDiag = clinicalDiag;
    }

    public String getPatientCondition() {
        return patientCondition;
    }

    public void setPatientCondition(String patientCondition) {
        this.patientCondition = patientCondition;
    }

    public LocalDateTime getApplyTime() {
        return applyTime;
    }

    public void setApplyTime(LocalDateTime applyTime) {
        this.applyTime = applyTime;
    }

    public LocalDateTime getReceiveTime() {
        return receiveTime;
    }

    public void setReceiveTime(LocalDateTime receiveTime) {
        this.receiveTime = receiveTime;
    }

    public LocalDateTime getCancelTime() {
        return cancelTime;
    }

    public void setCancelTime(LocalDateTime cancelTime) {
        this.cancelTime = cancelTime;
    }

    public Integer getHisSyncStatus() {
        return hisSyncStatus;
    }

    public void setHisSyncStatus(Integer hisSyncStatus) {
        this.hisSyncStatus = hisSyncStatus;
    }

    public Long getCollectionMethodId() {
        return collectionMethodId;
    }

    public void setCollectionMethodId(Long collectionMethodId) {
        this.collectionMethodId = collectionMethodId;
    }

    public String getCollectionMethodName() {
        return collectionMethodName;
    }

    public void setCollectionMethodName(String collectionMethodName) {
        this.collectionMethodName = collectionMethodName;
    }

    public LocalDateTime getCreateTime() {
        return createTime;
    }

    public void setCreateTime(LocalDateTime createTime) {
        this.createTime = createTime;
    }

    public String getHisApplyId() {
        return hisApplyId;
    }

    public void setHisApplyId(String hisApplyId) {
        this.hisApplyId = hisApplyId;
    }

}
