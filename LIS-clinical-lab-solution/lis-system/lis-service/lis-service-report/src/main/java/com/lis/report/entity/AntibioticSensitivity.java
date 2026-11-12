package com.lis.report.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.LocalDateTime;

/**
 * 药敏试验结果实体
 */
@TableName("lis_antibiotic_sensitivity")
public class AntibioticSensitivity {

    /** 结果: 敏感 */
    public static final String RESULT_SUSCEPTIBLE = "S";
    /** 结果: 耐药 */
    public static final String RESULT_RESISTANT = "R";
    /** 结果: 中介 */
    public static final String RESULT_INTERMEDIATE = "I";

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    /** 微生物报告ID */
    private Long microReportId;

    /** 细菌ID */
    private Long organismId;

    /** 抗生素名称 */
    private String antibioticName;

    /** 抗生素编码 */
    private String antibioticCode;

    /** MIC 值 */
    private String micValue;

    /** 结果: S=敏感 R=耐药 I=中介 */
    private String result;

    /** 折点 */
    private String breakpoint;

    /** 检测方法 (MIC/K-B 等) */
    private String method;

    /** 抑菌圈直径 (mm, K-B法使用) */
    private Integer zoneSize;

    /** 备注 */
    private String remark;

    /** 创建时间 */
    private LocalDateTime createTime;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getMicroReportId() {
        return microReportId;
    }

    public void setMicroReportId(Long microReportId) {
        this.microReportId = microReportId;
    }

    public Long getOrganismId() {
        return organismId;
    }

    public void setOrganismId(Long organismId) {
        this.organismId = organismId;
    }

    public String getAntibioticName() {
        return antibioticName;
    }

    public void setAntibioticName(String antibioticName) {
        this.antibioticName = antibioticName;
    }

    public String getAntibioticCode() {
        return antibioticCode;
    }

    public void setAntibioticCode(String antibioticCode) {
        this.antibioticCode = antibioticCode;
    }

    public String getMicValue() {
        return micValue;
    }

    public void setMicValue(String micValue) {
        this.micValue = micValue;
    }

    public String getResult() {
        return result;
    }

    public void setResult(String result) {
        this.result = result;
    }

    public String getBreakpoint() {
        return breakpoint;
    }

    public void setBreakpoint(String breakpoint) {
        this.breakpoint = breakpoint;
    }

    public String getMethod() {
        return method;
    }

    public void setMethod(String method) {
        this.method = method;
    }

    public Integer getZoneSize() {
        return zoneSize;
    }

    public void setZoneSize(Integer zoneSize) {
        this.zoneSize = zoneSize;
    }

    public String getRemark() {
        return remark;
    }

    public void setRemark(String remark) {
        this.remark = remark;
    }

    public LocalDateTime getCreateTime() {
        return createTime;
    }

    public void setCreateTime(LocalDateTime createTime) {
        this.createTime = createTime;
    }

}
