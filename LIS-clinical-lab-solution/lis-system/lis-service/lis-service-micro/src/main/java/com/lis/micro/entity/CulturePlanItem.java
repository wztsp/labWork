package com.lis.micro.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 培养方案明细
 * 一个方案包含的培养基类型及数量
 */
@TableName("lis_culture_plan_item")
public class CulturePlanItem implements Serializable {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 方案ID */
    private Long planId;

    /** 培养基类型ID */
    private Long mediumTypeId;

    /** 培养基名称 (冗余) */
    private String mediumTypeName;

    /** 数量 */
    private Integer quantity;

    /** 排序 */
    private Integer sortOrder;

    private LocalDateTime createTime;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getPlanId() { return planId; }
    public void setPlanId(Long planId) { this.planId = planId; }
    public Long getMediumTypeId() { return mediumTypeId; }
    public void setMediumTypeId(Long mediumTypeId) { this.mediumTypeId = mediumTypeId; }
    public String getMediumTypeName() { return mediumTypeName; }
    public void setMediumTypeName(String mediumTypeName) { this.mediumTypeName = mediumTypeName; }
    public Integer getQuantity() { return quantity; }
    public void setQuantity(Integer quantity) { this.quantity = quantity; }
    public Integer getSortOrder() { return sortOrder; }
    public void setSortOrder(Integer sortOrder) { this.sortOrder = sortOrder; }
    public LocalDateTime getCreateTime() { return createTime; }
    public void setCreateTime(LocalDateTime createTime) { this.createTime = createTime; }
}