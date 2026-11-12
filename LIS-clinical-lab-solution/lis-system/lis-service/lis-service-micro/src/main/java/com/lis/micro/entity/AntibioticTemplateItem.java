package com.lis.micro.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.LocalDateTime;

/**
 * 抗生素模板明细(模板包含的抗生素)
 */
@TableName("lis_antibiotic_template_item")
public class AntibioticTemplateItem {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 所属模板ID */
    private Long templateId;
    /** 抗生素字典ID，关联 lis_antibiotic.id */
    private Long antibioticId;
    /** 本条抗生素检测方法(MIC/KB)，空则继承模板头 defaultMethod */
    private String defaultMethod;
    /** 显示顺序（决定报告页药敏表插入顺序） */
    private Integer sortNo;
    /** 备注 */
    private String remark;
    private LocalDateTime createTime;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getTemplateId() { return templateId; }
    public void setTemplateId(Long templateId) { this.templateId = templateId; }
    public Long getAntibioticId() { return antibioticId; }
    public void setAntibioticId(Long antibioticId) { this.antibioticId = antibioticId; }
    public String getDefaultMethod() { return defaultMethod; }
    public void setDefaultMethod(String defaultMethod) { this.defaultMethod = defaultMethod; }
    public Integer getSortNo() { return sortNo; }
    public void setSortNo(Integer sortNo) { this.sortNo = sortNo; }
    public String getRemark() { return remark; }
    public void setRemark(String remark) { this.remark = remark; }
    public LocalDateTime getCreateTime() { return createTime; }
    public void setCreateTime(LocalDateTime createTime) { this.createTime = createTime; }
}