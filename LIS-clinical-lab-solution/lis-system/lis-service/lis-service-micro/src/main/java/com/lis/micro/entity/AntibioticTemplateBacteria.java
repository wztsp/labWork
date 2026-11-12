package com.lis.micro.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.LocalDateTime;

/**
 * 抗生素模板与细菌字典绑定关系(多对多)
 * 用于「选择细菌时匹配可用的抗生素模板」。
 */
@TableName("lis_antibiotic_template_bacteria")
public class AntibioticTemplateBacteria {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 模板ID */
    private Long templateId;
    /** 细菌字典ID，关联 lis_bacteria.id */
    private Long bacteriaId;
    private LocalDateTime createTime;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getTemplateId() { return templateId; }
    public void setTemplateId(Long templateId) { this.templateId = templateId; }
    public Long getBacteriaId() { return bacteriaId; }
    public void setBacteriaId(Long bacteriaId) { this.bacteriaId = bacteriaId; }
    public LocalDateTime getCreateTime() { return createTime; }
    public void setCreateTime(LocalDateTime createTime) { this.createTime = createTime; }
}