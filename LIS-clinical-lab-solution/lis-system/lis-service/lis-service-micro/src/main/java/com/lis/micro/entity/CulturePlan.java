package com.lis.micro.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 培养方案
 * 一个方案绑定一个检验项目(test_item_id), 定义该标本需接种的多个培养皿
 */
@TableName("lis_culture_plan")
public class CulturePlan implements Serializable {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 方案名称 */
    private String planName;

    /** 绑定的检验项目ID */
    private Long testItemId;

    /** 检验项目名称 (冗余) */
    private String testItemName;

    /**
     * 标本类型 (如 痰/尿液/血液/粪便/分泌物等)
     * 为空或NULL表示适用于全部标本类型
     */
    private String sampleType;

    /** 备注 */
    private String remark;

    /** 是否默认方案: 0否 1是 */
    private Integer isDefault;

    /** 状态: 0停用 1启用 */
    private Integer status;

    private LocalDateTime createTime;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getPlanName() { return planName; }
    public void setPlanName(String planName) { this.planName = planName; }
    public Long getTestItemId() { return testItemId; }
    public void setTestItemId(Long testItemId) { this.testItemId = testItemId; }
    public String getTestItemName() { return testItemName; }
    public void setTestItemName(String testItemName) { this.testItemName = testItemName; }
    public String getSampleType() { return sampleType; }
    public void setSampleType(String sampleType) { this.sampleType = sampleType; }
    public String getRemark() { return remark; }
    public void setRemark(String remark) { this.remark = remark; }
    public Integer getIsDefault() { return isDefault; }
    public void setIsDefault(Integer isDefault) { this.isDefault = isDefault; }
    public Integer getStatus() { return status; }
    public void setStatus(Integer status) { this.status = status; }
    public LocalDateTime getCreateTime() { return createTime; }
    public void setCreateTime(LocalDateTime createTime) { this.createTime = createTime; }
}