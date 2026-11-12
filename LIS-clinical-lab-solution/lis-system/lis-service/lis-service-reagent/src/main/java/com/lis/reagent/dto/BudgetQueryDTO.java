package com.lis.reagent.dto;

import java.io.Serializable;

/**
 * 预算分页查询入参。
 *
 * @author LIS Team
 */
public class BudgetQueryDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 预算年度 */
    private Integer budgetYear;
    /** 科室 ID */
    private Long deptId;
    /** 试剂分类 ID；为 null 表示不限类别的通用预算 */
    private Long categoryId;
    /** 状态：0停用 1启用 */
    private Integer status;
    /** 预算编号，模糊匹配 */
    private String budgetNo;
    /** 是否只看已触发预警的预算 */
    private Boolean onlyWarning;
    /** 页码，从 1 开始 */
    private Integer pageNum = 1;
    /** 每页条数 */
    private Integer pageSize = 10;

    public BudgetQueryDTO() {
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

    public Long getCategoryId() {
        return categoryId;
    }

    public void setCategoryId(Long categoryId) {
        this.categoryId = categoryId;
    }

    public Integer getStatus() {
        return status;
    }

    public void setStatus(Integer status) {
        this.status = status;
    }

    public String getBudgetNo() {
        return budgetNo;
    }

    public void setBudgetNo(String budgetNo) {
        this.budgetNo = budgetNo;
    }

    public Boolean getOnlyWarning() {
        return onlyWarning;
    }

    public void setOnlyWarning(Boolean onlyWarning) {
        this.onlyWarning = onlyWarning;
    }

    public Integer getPageNum() {
        return pageNum;
    }

    public void setPageNum(Integer pageNum) {
        this.pageNum = pageNum;
    }

    public Integer getPageSize() {
        return pageSize;
    }

    public void setPageSize(Integer pageSize) {
        this.pageSize = pageSize;
    }
}
