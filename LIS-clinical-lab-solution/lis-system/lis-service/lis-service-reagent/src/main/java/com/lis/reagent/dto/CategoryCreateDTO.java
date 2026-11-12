package com.lis.reagent.dto;

import java.io.Serializable;

/**
 * 试剂分类创建 / 编辑入参。
 *
 * <p>{@code path} 与 {@code levelNo} 由 Service 依据 {@code parentId} 自动计算，
 * 前端不传，防止树结构被非法篡改。</p>
 *
 * @author LIS Team
 */
public class CategoryCreateDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 分类 ID，新建时为 null */
    private Long id;
    /** 分类编码；为空时由 Service 生成，唯一 */
    private String categoryCode;
    /** 分类名称，必填 */
    private String categoryName;
    /** 上级分类 ID；null 或 0 表示顶级 */
    private Long parentId;
    /** 危险品类别标记：普通 / 易燃 / 易爆 / 剧毒 / 腐蚀 / 放射性 */
    private String hazardCategory;
    /** 排序号 */
    private Integer sortOrder;
    /** 状态：0停用 1启用 */
    private Integer status;
    /** 备注 */
    private String remark;

    public CategoryCreateDTO() {
    }

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

    public String getHazardCategory() {
        return hazardCategory;
    }

    public void setHazardCategory(String hazardCategory) {
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
}
