package com.lis.micro.dto;

import java.util.List;

/**
 * 报告页「应用抗生素模板」返回 VO。
 * 针对指定细菌，将模板内抗生素明细补全字典信息并解析折点，
 * 前端据此逐条批量写入药敏结果（MIC/结果留空，按折点自动加载）。
 */
public class AntibioticTemplateApplyVO {

    private Long templateId;
    private String templateName;
    private String defaultMethod;
    /** 命中折点来源: 专属折点表 / 抗生素字典 / 未配置 */
    private List<ApplyItem> items;

    public static class ApplyItem {
        private Long antibioticId;
        private String antibioticCode;
        private String antibioticName;
        private String category;
        /** 检测方法（项级 > 模板头） */
        private String method;
        /** 判读界值文案，如 "敏感(S)≤2 ; 耐药(R)≥8" */
        private String breakpoint;
        /** 折点来源说明 */
        private String breakpointSource;

        public Long getAntibioticId() { return antibioticId; }
        public void setAntibioticId(Long antibioticId) { this.antibioticId = antibioticId; }
        public String getAntibioticCode() { return antibioticCode; }
        public void setAntibioticCode(String antibioticCode) { this.antibioticCode = antibioticCode; }
        public String getAntibioticName() { return antibioticName; }
        public void setAntibioticName(String antibioticName) { this.antibioticName = antibioticName; }
        public String getCategory() { return category; }
        public void setCategory(String category) { this.category = category; }
        public String getMethod() { return method; }
        public void setMethod(String method) { this.method = method; }
        public String getBreakpoint() { return breakpoint; }
        public void setBreakpoint(String breakpoint) { this.breakpoint = breakpoint; }
        public String getBreakpointSource() { return breakpointSource; }
        public void setBreakpointSource(String breakpointSource) { this.breakpointSource = breakpointSource; }
    }

    public Long getTemplateId() { return templateId; }
    public void setTemplateId(Long templateId) { this.templateId = templateId; }
    public String getTemplateName() { return templateName; }
    public void setTemplateName(String templateName) { this.templateName = templateName; }
    public String getDefaultMethod() { return defaultMethod; }
    public void setDefaultMethod(String defaultMethod) { this.defaultMethod = defaultMethod; }
    public List<ApplyItem> getItems() { return items; }
    public void setItems(List<ApplyItem> items) { this.items = items; }
}