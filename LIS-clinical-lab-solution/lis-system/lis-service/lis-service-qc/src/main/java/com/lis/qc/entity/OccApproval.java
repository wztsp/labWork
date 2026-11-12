package com.lis.qc.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.LocalDateTime;

/**
 * 失控事件三级审批记录（occ-module-design.md 3.4）。
 * 注: 手写 getter/setter 替代 Lombok @Data, 保证编译稳定性。
 */
@TableName("lis_qc_occ_approval")
public class OccApproval {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long occId;
    /** 1=专业组组长 2=质控负责人 3=科主任 */
    private Integer levelNo;
    private String levelName;
    /** PENDING / APPROVED / REJECTED */
    private String status;
    private String approver;
    private String comment;
    private LocalDateTime approvedAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getOccId() { return occId; }
    public void setOccId(Long occId) { this.occId = occId; }
    public Integer getLevelNo() { return levelNo; }
    public void setLevelNo(Integer levelNo) { this.levelNo = levelNo; }
    public String getLevelName() { return levelName; }
    public void setLevelName(String levelName) { this.levelName = levelName; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getApprover() { return approver; }
    public void setApprover(String approver) { this.approver = approver; }
    public String getComment() { return comment; }
    public void setComment(String comment) { this.comment = comment; }
    public LocalDateTime getApprovedAt() { return approvedAt; }
    public void setApprovedAt(LocalDateTime approvedAt) { this.approvedAt = approvedAt; }
}
