package com.lis.reagent.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 试剂预算主表（年度 + 科室 + 类别 三维）。
 *
 * <p>并发安全依赖：{@code ReagentBudgetMapper.selectByIdForUpdate()} 悲观行锁 +
 * {@code BudgetGuard.judge()} 纯 POJO 判定 + {@code tryFreeze()} 条件 CAS 兜底。
 * {@code version} 列仅作双保险与审计，不单独使用 {@code @Version} 乐观锁
 * （方案 A 无法区分「并发冲突」与「超预算」，见设计文档 §2.2.2）。</p>
 *
 * <p>手写 getter/setter，不使用 Lombok（C-10）。不声明 {@code deleted} 字段（C-9 复核结论）。</p>
 */
@TableName("lis_reagent_budget")
public class ReagentBudget {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 预算编号（可选，展示用） */
    private String budgetNo;
    /** 预算年度 */
    private Integer budgetYear;
    /** 科室ID（sys_department.id 快照，不建外键，跨模块解耦） */
    private Long deptId;
    /** 科室名称冗余快照 */
    private String deptName;
    /** 试剂类别ID，NULL=不限类别（通用预算） */
    private Long categoryId;
    /** 类别名称冗余快照 */
    private String categoryName;
    /** 预算总额 */
    private BigDecimal totalAmount;
    /** 已占用（申请提交时 +，驳回/作废时 -，实扣时 -） */
    private BigDecimal frozenAmount;
    /** 已实扣（入库验收通过时 +） */
    private BigDecimal usedAmount;
    /** 预警阈值，默认 0.8000 */
    private BigDecimal warnRatio;
    /** 版本号（配合 FOR UPDATE 做双保险，由 SQL 自增，不用 @Version） */
    private Integer version;
    /** 0停用 1启用 */
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

    public String getBudgetNo() {
        return budgetNo;
    }

    public void setBudgetNo(String budgetNo) {
        this.budgetNo = budgetNo;
    }

    public Integer getBudgetYear() {
        return budgetYear;
    }

    public void setBudgetYear(Integer budgetYear) {
        this.budgetYear = budgetYear;
    }

    public Long getDeptId() {
        return deptId;
    }

    public void setDeptId(Long deptId) {
        this.deptId = deptId;
    }

    public String getDeptName() {
        return deptName;
    }

    public void setDeptName(String deptName) {
        this.deptName = deptName;
    }

    public Long getCategoryId() {
        return categoryId;
    }

    public void setCategoryId(Long categoryId) {
        this.categoryId = categoryId;
    }

    public String getCategoryName() {
        return categoryName;
    }

    public void setCategoryName(String categoryName) {
        this.categoryName = categoryName;
    }

    public BigDecimal getTotalAmount() {
        return totalAmount;
    }

    public void setTotalAmount(BigDecimal totalAmount) {
        this.totalAmount = totalAmount;
    }

    public BigDecimal getFrozenAmount() {
        return frozenAmount;
    }

    public void setFrozenAmount(BigDecimal frozenAmount) {
        this.frozenAmount = frozenAmount;
    }

    public BigDecimal getUsedAmount() {
        return usedAmount;
    }

    public void setUsedAmount(BigDecimal usedAmount) {
        this.usedAmount = usedAmount;
    }

    public BigDecimal getWarnRatio() {
        return warnRatio;
    }

    public void setWarnRatio(BigDecimal warnRatio) {
        this.warnRatio = warnRatio;
    }

    public Integer getVersion() {
        return version;
    }

    public void setVersion(Integer version) {
        this.version = version;
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
