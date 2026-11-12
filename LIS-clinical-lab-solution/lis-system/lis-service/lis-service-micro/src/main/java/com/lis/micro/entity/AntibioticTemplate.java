package com.lis.micro.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.LocalDateTime;

/**
 * 抗生素药敏模板主表
 */
@TableName("lis_antibiotic_template")
public class AntibioticTemplate {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 模板编码，如 ABX_GP_ROUTINE */
    private String code;
    /** 模板名称，如「G+ 常规药敏板」 */
    private String name;
    /** 分类(预设): G+/G-/非发酵/苛养/厌氧/真菌 */
    private String templateCategory;
    /** 默认检测方法(MIC/KB)，为空表示不预设 */
    private String defaultMethod;
    /** 描述：适用仪器、板子货号、适用场景 */
    private String description;
    /** 状态: 1 启用 / 0 禁用（禁用模板在报告页下拉不展示） */
    private Integer status;
    /** 是否系统内置: 1 是(不可删除) / 0 客户自建 */
    private Integer isSystem;
    /** 同级排序 */
    private Integer sortNo;
    /** 创建人 ID */
    private Long createUserId;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getCode() { return code; }
    public void setCode(String code) { this.code = code; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getTemplateCategory() { return templateCategory; }
    public void setTemplateCategory(String templateCategory) { this.templateCategory = templateCategory; }
    public String getDefaultMethod() { return defaultMethod; }
    public void setDefaultMethod(String defaultMethod) { this.defaultMethod = defaultMethod; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public Integer getStatus() { return status; }
    public void setStatus(Integer status) { this.status = status; }
    public Integer getIsSystem() { return isSystem; }
    public void setIsSystem(Integer isSystem) { this.isSystem = isSystem; }
    public Integer getSortNo() { return sortNo; }
    public void setSortNo(Integer sortNo) { this.sortNo = sortNo; }
    public Long getCreateUserId() { return createUserId; }
    public void setCreateUserId(Long createUserId) { this.createUserId = createUserId; }
    public LocalDateTime getCreateTime() { return createTime; }
    public void setCreateTime(LocalDateTime createTime) { this.createTime = createTime; }
    public LocalDateTime getUpdateTime() { return updateTime; }
    public void setUpdateTime(LocalDateTime updateTime) { this.updateTime = updateTime; }
}