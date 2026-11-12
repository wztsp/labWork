package com.lis.reagent.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.LocalDateTime;

/**
 * 试剂采购审批流水（只增不改不删）。
 *
 * <p>满足 R-02 验收 3「审批历史不可删改」：Service 层只提供 insert，
 * 不提供 update/delete 方法，Controller 不暴露写端点（吸取缺陷 O 教训）。</p>
 *
 * <p>驳回后重新提交时 {@code roundNo} +1，{@code nodeLevel} 从 1 重走。</p>
 *
 * <p>手写 getter/setter，不使用 Lombok（C-10）。不声明 {@code deleted} 字段（C-9 复核结论）。</p>
 */
@TableName("lis_reagent_purchase_approval")
public class ReagentPurchaseApproval {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long applyId;
    private String applyNo;
    private Integer nodeLevel;
    private String nodeName;
    /** 1通过 2驳回，见 {@code com.lis.reagent.enums.ApprovalAction#getRecordCode()} */
    private Integer action;
    private Long approverId;
    private String approverName;
    /** 审批意见 */
    private String opinion;
    private LocalDateTime approveTime;
    /** 第几轮（驳回后重新提交，轮次 +1） */
    private Integer roundNo;
    private LocalDateTime createTime;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getApplyId() {
        return applyId;
    }

    public void setApplyId(Long applyId) {
        this.applyId = applyId;
    }

    public String getApplyNo() {
        return applyNo;
    }

    public void setApplyNo(String applyNo) {
        this.applyNo = applyNo;
    }

    public Integer getNodeLevel() {
        return nodeLevel;
    }

    public void setNodeLevel(Integer nodeLevel) {
        this.nodeLevel = nodeLevel;
    }

    public String getNodeName() {
        return nodeName;
    }

    public void setNodeName(String nodeName) {
        this.nodeName = nodeName;
    }

    public Integer getAction() {
        return action;
    }

    public void setAction(Integer action) {
        this.action = action;
    }

    public Long getApproverId() {
        return approverId;
    }

    public void setApproverId(Long approverId) {
        this.approverId = approverId;
    }

    public String getApproverName() {
        return approverName;
    }

    public void setApproverName(String approverName) {
        this.approverName = approverName;
    }

    public String getOpinion() {
        return opinion;
    }

    public void setOpinion(String opinion) {
        this.opinion = opinion;
    }

    public LocalDateTime getApproveTime() {
        return approveTime;
    }

    public void setApproveTime(LocalDateTime approveTime) {
        this.approveTime = approveTime;
    }

    public Integer getRoundNo() {
        return roundNo;
    }

    public void setRoundNo(Integer roundNo) {
        this.roundNo = roundNo;
    }

    public LocalDateTime getCreateTime() {
        return createTime;
    }

    public void setCreateTime(LocalDateTime createTime) {
        this.createTime = createTime;
    }

}
