package com.lis.reagent.dto;

import java.io.Serializable;

/**
 * 审批动作入参（采购申请审批 / 驳回 / 作废 / 解冻通用）。
 *
 * @author LIS Team
 */
public class ApprovalDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 单据 ID，必填 */
    private Long applyId;
    /**
     * 审批动作码，取 {@code ApprovalAction.getRecordCode()}：1通过 2驳回。
     * 作废 / 解冻走独立端点，不复用本字段。
     */
    private Integer action;
    /** 审批人 ID */
    private Long approverId;
    /** 审批人姓名 */
    private String approverName;
    /** 审批意见；驳回时必填 */
    private String opinion;

    public ApprovalDTO() {
    }

    public Long getApplyId() {
        return applyId;
    }

    public void setApplyId(Long applyId) {
        this.applyId = applyId;
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
}
