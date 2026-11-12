package com.lis.micro.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.LocalDateTime;

/**
 * 微生物报告打印模板配置
 */
@TableName("lis_micro_report_print_config")
public class MicroReportPrintConfig {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String configName;
    private Integer showMic;
    private Integer showKb;
    private Integer showBreakpoint;
    private Integer showMethod;
    /** JSON 数组: 隐藏的抗生素 */
    private String hiddenAntibiotics;
    /** JSON 数组: 排序 */
    private String orderByCategories;
    private String breakpointVersion;
    private String paperSize;
    private Integer isDefault;
    private LocalDateTime createTime;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getConfigName() {
        return configName;
    }

    public void setConfigName(String configName) {
        this.configName = configName;
    }

    public Integer getShowMic() {
        return showMic;
    }

    public void setShowMic(Integer showMic) {
        this.showMic = showMic;
    }

    public Integer getShowKb() {
        return showKb;
    }

    public void setShowKb(Integer showKb) {
        this.showKb = showKb;
    }

    public Integer getShowBreakpoint() {
        return showBreakpoint;
    }

    public void setShowBreakpoint(Integer showBreakpoint) {
        this.showBreakpoint = showBreakpoint;
    }

    public Integer getShowMethod() {
        return showMethod;
    }

    public void setShowMethod(Integer showMethod) {
        this.showMethod = showMethod;
    }

    public String getHiddenAntibiotics() {
        return hiddenAntibiotics;
    }

    public void setHiddenAntibiotics(String hiddenAntibiotics) {
        this.hiddenAntibiotics = hiddenAntibiotics;
    }

    public String getOrderByCategories() {
        return orderByCategories;
    }

    public void setOrderByCategories(String orderByCategories) {
        this.orderByCategories = orderByCategories;
    }

    public String getBreakpointVersion() {
        return breakpointVersion;
    }

    public void setBreakpointVersion(String breakpointVersion) {
        this.breakpointVersion = breakpointVersion;
    }

    public String getPaperSize() {
        return paperSize;
    }

    public void setPaperSize(String paperSize) {
        this.paperSize = paperSize;
    }

    public Integer getIsDefault() {
        return isDefault;
    }

    public void setIsDefault(Integer isDefault) {
        this.isDefault = isDefault;
    }

    public LocalDateTime getCreateTime() {
        return createTime;
    }

    public void setCreateTime(LocalDateTime createTime) {
        this.createTime = createTime;
    }

}
