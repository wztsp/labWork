package com.lis.reagent.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.io.Serializable;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 试剂报废环保处置扩展实体，与报废单（{@code reqType=3}）1:1。
 *
 * <p>对应表 {@code lis_reagent_scrap_disposal}。本表不承载独立审批流程，
 * 仅作为报废单的环保合规附加信息，第二批 R-10 使用，本批仅建表建实体。</p>
 *
 * @author LIS Team
 */
@TableName("lis_reagent_scrap_disposal")
public class ReagentScrapDisposal implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 主键 */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 关联报废单 ID（lis_reagent_requisition.id，reqType=3），唯一 */
    private Long reqId;

    /** 处置方式：焚烧 / 中和 / 委托回收 / 填埋 */
    private String disposalMethod;

    /** 处置单位名称 */
    private String disposalOrg;

    /** 处置凭证号 */
    private String disposalCert;

    /** 处置日期 */
    private LocalDate disposalDate;

    /** 是否危险品：0否 1是 */
    private Integer hazardFlag;

    /** 危险等级：易燃 / 易爆 / 剧毒 / 腐蚀 / 放射性 */
    private String hazardClass;

    /** 安全员 ID */
    private Long safetyApproverId;

    /** 安全员姓名快照 */
    private String safetyApproverName;

    /** 安全员审核时间 */
    private LocalDateTime safetyApproveTime;

    /** 备注 */
    private String remark;

    /** 创建时间 */
    private LocalDateTime createTime;

    /** 更新时间 */
    private LocalDateTime updateTime;

    public ReagentScrapDisposal() {
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getReqId() {
        return reqId;
    }

    public void setReqId(Long reqId) {
        this.reqId = reqId;
    }

    public String getDisposalMethod() {
        return disposalMethod;
    }

    public void setDisposalMethod(String disposalMethod) {
        this.disposalMethod = disposalMethod;
    }

    public String getDisposalOrg() {
        return disposalOrg;
    }

    public void setDisposalOrg(String disposalOrg) {
        this.disposalOrg = disposalOrg;
    }

    public String getDisposalCert() {
        return disposalCert;
    }

    public void setDisposalCert(String disposalCert) {
        this.disposalCert = disposalCert;
    }

    public LocalDate getDisposalDate() {
        return disposalDate;
    }

    public void setDisposalDate(LocalDate disposalDate) {
        this.disposalDate = disposalDate;
    }

    public Integer getHazardFlag() {
        return hazardFlag;
    }

    public void setHazardFlag(Integer hazardFlag) {
        this.hazardFlag = hazardFlag;
    }

    public String getHazardClass() {
        return hazardClass;
    }

    public void setHazardClass(String hazardClass) {
        this.hazardClass = hazardClass;
    }

    public Long getSafetyApproverId() {
        return safetyApproverId;
    }

    public void setSafetyApproverId(Long safetyApproverId) {
        this.safetyApproverId = safetyApproverId;
    }

    public String getSafetyApproverName() {
        return safetyApproverName;
    }

    public void setSafetyApproverName(String safetyApproverName) {
        this.safetyApproverName = safetyApproverName;
    }

    public LocalDateTime getSafetyApproveTime() {
        return safetyApproveTime;
    }

    public void setSafetyApproveTime(LocalDateTime safetyApproveTime) {
        this.safetyApproveTime = safetyApproveTime;
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
