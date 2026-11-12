package com.lis.reagent.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.LocalDateTime;

/**
 * 试剂审批节点定义（配置表）。
 *
 * <p>PRD 5.1 的 13 张表清单遗漏了本表，实际新增表为 14 张（C-6）。</p>
 *
 * <p>{@code triggerCond} 用枚举字符串（ALWAYS / OVER_BUDGET / HAZARD），
 * 绝不引入表达式引擎；节点解析在纯 POJO {@code ApprovalNodeResolver} 中完成。</p>
 *
 * <p>{@code status} 同时充当「该业务域是否需要审批」的开关，替代 reagent 模块访问不到的
 * {@code sys_config}（C-5）。</p>
 *
 * <p>{@code approverRoleCode} 指向现有 {@code sys_role.role_code}（主理人 U-4：复用现有角色体系）。</p>
 *
 * <p>手写 getter/setter，不使用 Lombok（C-10）。不声明 {@code deleted} 字段（C-9 复核结论）。</p>
 */
@TableName("lis_reagent_approval_node")
public class ReagentApprovalNode {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** PURCHASE 采购申请 / REQUISITION 出入库业务单 */
    private String bizType;
    /** 节点顺序 1,2,3... */
    private Integer nodeLevel;
    /** 节点名称 */
    private String nodeName;
    /** ALWAYS 恒触发 / OVER_BUDGET 超预算触发 / HAZARD 危险品触发 */
    private String triggerCond;
    /** 审批人角色编码（指向 sys_role.role_code） */
    private String approverRoleCode;
    /** 1本科室 2全院 */
    private Integer approverDeptScope;
    /** 0停用 1启用（兼作审批开关） */
    private Integer status;
    private String remark;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getBizType() {
        return bizType;
    }

    public void setBizType(String bizType) {
        this.bizType = bizType;
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

    public String getTriggerCond() {
        return triggerCond;
    }

    public void setTriggerCond(String triggerCond) {
        this.triggerCond = triggerCond;
    }

    public String getApproverRoleCode() {
        return approverRoleCode;
    }

    public void setApproverRoleCode(String approverRoleCode) {
        this.approverRoleCode = approverRoleCode;
    }

    public Integer getApproverDeptScope() {
        return approverDeptScope;
    }

    public void setApproverDeptScope(Integer approverDeptScope) {
        this.approverDeptScope = approverDeptScope;
    }

    public Integer getStatus() {
        return status;
    }

    public void setStatus(Integer status) {
        this.status = status;
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
