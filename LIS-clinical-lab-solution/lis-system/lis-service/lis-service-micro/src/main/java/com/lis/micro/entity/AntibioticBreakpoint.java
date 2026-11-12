package com.lis.micro.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.LocalDateTime;

import static com.fasterxml.jackson.annotation.JsonAutoDetect.Visibility.ANY;
import static com.fasterxml.jackson.annotation.JsonAutoDetect.Visibility.NONE;

/**
 * 抗生素折点表
 * <p>只按字段可见性序列化，避免 Jackson 默认 getter 命名（getSBreakpoint → sbreakpoint）与 @JsonProperty 冲突，
 * 从而产生重复 JSON key。</p>
 */
@JsonAutoDetect(getterVisibility = NONE, isGetterVisibility = NONE, setterVisibility = NONE, fieldVisibility = ANY)
@TableName("lis_antibiotic_breakpoint")
public class AntibioticBreakpoint {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 抗生素ID，关联 lis_antibiotic.id */
    private Long antibioticId;
    /** 细菌ID，关联 lis_bacteria.id */
    private Long bacteriaId;
    /** 方法学: K-B纸片法/微量肉汤法/E-test */
    private String method;
    /** 标准: CLSI/EUCAST */
    private String standard;
    /** 标准版本，如 M100-2023 */
    private String standardVersion;
    /** 敏感界值(S)，如 ≤2 */
    @JsonProperty("sBreakpoint")
    private String sBreakpoint;
    /** 耐药界值(R)，如 ≥8 */
    @JsonProperty("rBreakpoint")
    private String rBreakpoint;
    /** 中介界值(I) */
    @JsonProperty("iBreakpoint")
    private String iBreakpoint;
    /** K-B法敏感抑菌圈直径，如 ≥18 */
    @JsonProperty("sDd")
    private String sDd;
    /** K-B法耐药抑菌圈直径，如 ≤14 */
    @JsonProperty("rDd")
    private String rDd;
    /** 状态 0停用 1启用 */
    private Integer status;
    private LocalDateTime createTime;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getAntibioticId() {
        return antibioticId;
    }

    public void setAntibioticId(Long antibioticId) {
        this.antibioticId = antibioticId;
    }

    public Long getBacteriaId() {
        return bacteriaId;
    }

    public void setBacteriaId(Long bacteriaId) {
        this.bacteriaId = bacteriaId;
    }

    public String getMethod() {
        return method;
    }

    public void setMethod(String method) {
        this.method = method;
    }

    public String getStandard() {
        return standard;
    }

    public void setStandard(String standard) {
        this.standard = standard;
    }

    public String getStandardVersion() {
        return standardVersion;
    }

    public void setStandardVersion(String standardVersion) {
        this.standardVersion = standardVersion;
    }

    public String getSBreakpoint() {
        return sBreakpoint;
    }

    public void setSBreakpoint(String sBreakpoint) {
        this.sBreakpoint = sBreakpoint;
    }

    public String getRBreakpoint() {
        return rBreakpoint;
    }

    public void setRBreakpoint(String rBreakpoint) {
        this.rBreakpoint = rBreakpoint;
    }

    public String getIBreakpoint() {
        return iBreakpoint;
    }

    public void setIBreakpoint(String iBreakpoint) {
        this.iBreakpoint = iBreakpoint;
    }

    public String getSDd() {
        return sDd;
    }

    public void setSDd(String sDd) {
        this.sDd = sDd;
    }

    public String getRDd() {
        return rDd;
    }

    public void setRDd(String rDd) {
        this.rDd = rDd;
    }

    public Integer getStatus() {
        return status;
    }

    public void setStatus(Integer status) {
        this.status = status;
    }

    public LocalDateTime getCreateTime() {
        return createTime;
    }

    public void setCreateTime(LocalDateTime createTime) {
        this.createTime = createTime;
    }

}
