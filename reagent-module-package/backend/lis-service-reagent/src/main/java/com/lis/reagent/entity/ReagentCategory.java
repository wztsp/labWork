package com.lis.reagent.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 试剂分类字典（树形）。
 *
 * <p>用于填实 {@code lis_reagent.category_id} 悬空外键。树形结构采用物化路径
 * {@code path}（如 {@code /CAT_CHEM/CAT_ACID/}），子树查询用 likeRight，避免递归 CTE 与 N+1。</p>
 *
 * <p>手写 getter/setter，不使用 Lombok（C-10，与存量 {@code Reagent.java} 一致）。
 * 不声明 {@code deleted} 字段，逻辑删除语义统一用 {@code status}（C-9 复核结论）。</p>
 */
@TableName("lis_reagent_category")
public class ReagentCategory {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 分类编码，唯一 */
    private String categoryCode;
    /** 分类名称 */
    private String categoryName;
    /** 父分类ID，0=根 */
    private Long parentId;
    /** 物化路径，如 /CAT_CHEM/ */
    private String path;
    /** 层级 1/2/3 */
    private Integer levelNo;
    /** 0普通 1危险品类（该类下试剂默认危险品） */
    private Integer hazardCategory;
    /** 排序号 */
    private Integer sortOrder;
    /** 0停用 1启用 */
    private Integer status;
    private String remark;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;

    /** 子分类列表（非数据库字段，分类树组装用） */
    @TableField(exist = false)
    private List<ReagentCategory> children;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getCategoryCode() {
        return categoryCode;
    }

    public void setCategoryCode(String categoryCode) {
        this.categoryCode = categoryCode;
    }

    public String getCategoryName() {
        return categoryName;
    }

    public void setCategoryName(String categoryName) {
        this.categoryName = categoryName;
    }

    public Long getParentId() {
        return parentId;
    }

    public void setParentId(Long parentId) {
        this.parentId = parentId;
    }

    public String getPath() {
        return path;
    }

    public void setPath(String path) {
        this.path = path;
    }

    public Integer getLevelNo() {
        return levelNo;
    }

    public void setLevelNo(Integer levelNo) {
        this.levelNo = levelNo;
    }

    public Integer getHazardCategory() {
        return hazardCategory;
    }

    public void setHazardCategory(Integer hazardCategory) {
        this.hazardCategory = hazardCategory;
    }

    public Integer getSortOrder() {
        return sortOrder;
    }

    public void setSortOrder(Integer sortOrder) {
        this.sortOrder = sortOrder;
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

    public List<ReagentCategory> getChildren() {
        return children;
    }

    public void setChildren(List<ReagentCategory> children) {
        this.children = children;
    }

}
