package com.lis.micro.dto;

import java.util.List;

/**
 * 抗生素模板详情 VO（模板头 + 绑定细菌 + 抗生素明细）
 * 供前端新增/编辑弹窗回显，以及报告页「应用模板」获取明细。
 */
public class AntibioticTemplateVO {

    private Long id;
    private String code;
    private String name;
    private String templateCategory;
    private String defaultMethod;
    private String description;
    private Integer status;
    private Integer isSystem;
    private Integer sortNo;
    private Long createUserId;
    private String createTime;

    /** 抗生素明细条数（列表展示用，后端填充） */
    private Integer itemCount;
    /** 绑定的细菌字典ID集合 */
    private List<Long> bacteriaIds;
    /** 绑定的细菌名称（展示用，后端填充） */
    private List<String> bacteriaNames;

    /** 抗生素明细（含字典名称/编码补全） */
    private List<ItemVO> items;

    public static class ItemVO {
        private Long id;
        private Long antibioticId;
        private String antibioticCode;
        private String antibioticName;
        private String category;
        private String defaultMethod;
        private Integer sortNo;
        private String remark;

        public Long getId() { return id; }
        public void setId(Long id) { this.id = id; }
        public Long getAntibioticId() { return antibioticId; }
        public void setAntibioticId(Long antibioticId) { this.antibioticId = antibioticId; }
        public String getAntibioticCode() { return antibioticCode; }
        public void setAntibioticCode(String antibioticCode) { this.antibioticCode = antibioticCode; }
        public String getAntibioticName() { return antibioticName; }
        public void setAntibioticName(String antibioticName) { this.antibioticName = antibioticName; }
        public String getCategory() { return category; }
        public void setCategory(String category) { this.category = category; }
        public String getDefaultMethod() { return defaultMethod; }
        public void setDefaultMethod(String defaultMethod) { this.defaultMethod = defaultMethod; }
        public Integer getSortNo() { return sortNo; }
        public void setSortNo(Integer sortNo) { this.sortNo = sortNo; }
        public String getRemark() { return remark; }
        public void setRemark(String remark) { this.remark = remark; }
    }

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
    public String getCreateTime() { return createTime; }
    public void setCreateTime(String createTime) { this.createTime = createTime; }
    public Integer getItemCount() { return itemCount; }
    public void setItemCount(Integer itemCount) { this.itemCount = itemCount; }
    public List<Long> getBacteriaIds() { return bacteriaIds; }
    public void setBacteriaIds(List<Long> bacteriaIds) { this.bacteriaIds = bacteriaIds; }
    public List<String> getBacteriaNames() { return bacteriaNames; }
    public void setBacteriaNames(List<String> bacteriaNames) { this.bacteriaNames = bacteriaNames; }
    public List<ItemVO> getItems() { return items; }
    public void setItems(List<ItemVO> items) { this.items = items; }
}