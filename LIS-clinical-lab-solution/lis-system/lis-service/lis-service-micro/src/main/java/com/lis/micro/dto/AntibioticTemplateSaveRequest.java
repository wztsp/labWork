package com.lis.micro.dto;

import java.util.List;

/**
 * 保存抗生素模板请求（模板头 + 绑定细菌 + 抗生素明细）
 */
public class AntibioticTemplateSaveRequest {

    /** 模板主表字段 */
    private Long id;
    private String code;
    private String name;
    private String templateCategory;
    private String defaultMethod;
    private String description;
    private Integer status;
    private Integer sortNo;

    /** 绑定的细菌字典ID集合 */
    private List<Long> bacteriaIds;
    /** 抗生素明细（每项携带 antibioticId / defaultMethod / sortNo / remark） */
    private List<ItemReq> items;

    public static class ItemReq {
        private Long antibioticId;
        private String defaultMethod;
        private Integer sortNo;
        private String remark;

        public Long getAntibioticId() { return antibioticId; }
        public void setAntibioticId(Long antibioticId) { this.antibioticId = antibioticId; }
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
    public Integer getSortNo() { return sortNo; }
    public void setSortNo(Integer sortNo) { this.sortNo = sortNo; }
    public List<Long> getBacteriaIds() { return bacteriaIds; }
    public void setBacteriaIds(List<Long> bacteriaIds) { this.bacteriaIds = bacteriaIds; }
    public List<ItemReq> getItems() { return items; }
    public void setItems(List<ItemReq> items) { this.items = items; }
}