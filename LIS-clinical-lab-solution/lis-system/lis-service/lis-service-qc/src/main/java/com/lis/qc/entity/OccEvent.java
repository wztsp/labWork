package com.lis.qc.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 失控事件实体（OCC，对应 occ-module-design.md）。
 * 状态流: PENDING 待处理 → INVESTIGATING 排查中 → CORRECTED 已纠正 → CLOSED 已关闭。
 * 注: 原 @Data 依赖 Lombok 注解处理器, 改手写 getter/setter 以保证编译稳定性。
 */
@TableName("lis_qc_occ_event")
public class OccEvent {

    @TableId(type = IdType.AUTO)
    private Long id;
    private String occNo;
    private Long lotId;
    private String projectCode;
    private String projectName;
    private String groupName;
    private String triggerRules;
    private LocalDateTime foundAt;
    private String foundBy;
    /** PENDING / INVESTIGATING / CORRECTED / CLOSED */
    private String status;
    /** SYSTEM / RANDOM */
    private String errorType;
    /** 逗号分隔质控值快照 */
    private String qcValues;
    private BigDecimal targetValue;
    private BigDecimal sd;
    private String rootCauseCategory;
    private String rootCauseDesc;
    private String rootCauseBy;
    private LocalDateTime rootCauseAt;
    private String resultImpact;
    private String prevention;
    private String materialSnapshot;
    private Integer currentStep;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
    @TableLogic
    private Integer deleted;

    @TableField(exist = false)
    private List<OccStep> steps;
    @TableField(exist = false)
    private List<OccApproval> approvals;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getOccNo() { return occNo; }
    public void setOccNo(String occNo) { this.occNo = occNo; }
    public Long getLotId() { return lotId; }
    public void setLotId(Long lotId) { this.lotId = lotId; }
    public String getProjectCode() { return projectCode; }
    public void setProjectCode(String projectCode) { this.projectCode = projectCode; }
    public String getProjectName() { return projectName; }
    public void setProjectName(String projectName) { this.projectName = projectName; }
    public String getGroupName() { return groupName; }
    public void setGroupName(String groupName) { this.groupName = groupName; }
    public String getTriggerRules() { return triggerRules; }
    public void setTriggerRules(String triggerRules) { this.triggerRules = triggerRules; }
    public LocalDateTime getFoundAt() { return foundAt; }
    public void setFoundAt(LocalDateTime foundAt) { this.foundAt = foundAt; }
    public String getFoundBy() { return foundBy; }
    public void setFoundBy(String foundBy) { this.foundBy = foundBy; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getErrorType() { return errorType; }
    public void setErrorType(String errorType) { this.errorType = errorType; }
    public String getQcValues() { return qcValues; }
    public void setQcValues(String qcValues) { this.qcValues = qcValues; }
    public BigDecimal getTargetValue() { return targetValue; }
    public void setTargetValue(BigDecimal targetValue) { this.targetValue = targetValue; }
    public BigDecimal getSd() { return sd; }
    public void setSd(BigDecimal sd) { this.sd = sd; }
    public String getRootCauseCategory() { return rootCauseCategory; }
    public void setRootCauseCategory(String rootCauseCategory) { this.rootCauseCategory = rootCauseCategory; }
    public String getRootCauseDesc() { return rootCauseDesc; }
    public void setRootCauseDesc(String rootCauseDesc) { this.rootCauseDesc = rootCauseDesc; }
    public String getRootCauseBy() { return rootCauseBy; }
    public void setRootCauseBy(String rootCauseBy) { this.rootCauseBy = rootCauseBy; }
    public LocalDateTime getRootCauseAt() { return rootCauseAt; }
    public void setRootCauseAt(LocalDateTime rootCauseAt) { this.rootCauseAt = rootCauseAt; }
    public String getResultImpact() { return resultImpact; }
    public void setResultImpact(String resultImpact) { this.resultImpact = resultImpact; }
    public String getPrevention() { return prevention; }
    public void setPrevention(String prevention) { this.prevention = prevention; }
    public String getMaterialSnapshot() { return materialSnapshot; }
    public void setMaterialSnapshot(String materialSnapshot) { this.materialSnapshot = materialSnapshot; }
    public Integer getCurrentStep() { return currentStep; }
    public void setCurrentStep(Integer currentStep) { this.currentStep = currentStep; }
    public LocalDateTime getCreateTime() { return createTime; }
    public void setCreateTime(LocalDateTime createTime) { this.createTime = createTime; }
    public LocalDateTime getUpdateTime() { return updateTime; }
    public void setUpdateTime(LocalDateTime updateTime) { this.updateTime = updateTime; }
    public Integer getDeleted() { return deleted; }
    public void setDeleted(Integer deleted) { this.deleted = deleted; }
    public List<OccStep> getSteps() { return steps; }
    public void setSteps(List<OccStep> steps) { this.steps = steps; }
    public List<OccApproval> getApprovals() { return approvals; }
    public void setApprovals(List<OccApproval> approvals) { this.approvals = approvals; }
}
