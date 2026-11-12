package com.lis.qc.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.LocalDateTime;

/**
 * 失控事件七步法步骤记录（occ-module-design.md 3.2）。
 * 注: 手写 getter/setter 替代 Lombok @Data, 保证编译稳定性。
 */
@TableName("lis_qc_occ_step")
public class OccStep {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long occId;
    private Integer stepNo;
    private String name;
    /** PENDING / CURRENT / DONE */
    private String status;
    /** JSON: [{"text":..,"done":true}] */
    private String checklist;
    private String operator;
    private LocalDateTime operatedAt;
    private String conclusion;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getOccId() { return occId; }
    public void setOccId(Long occId) { this.occId = occId; }
    public Integer getStepNo() { return stepNo; }
    public void setStepNo(Integer stepNo) { this.stepNo = stepNo; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getChecklist() { return checklist; }
    public void setChecklist(String checklist) { this.checklist = checklist; }
    public String getOperator() { return operator; }
    public void setOperator(String operator) { this.operator = operator; }
    public LocalDateTime getOperatedAt() { return operatedAt; }
    public void setOperatedAt(LocalDateTime operatedAt) { this.operatedAt = operatedAt; }
    public String getConclusion() { return conclusion; }
    public void setConclusion(String conclusion) { this.conclusion = conclusion; }
}
