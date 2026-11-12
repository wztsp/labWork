package com.lis.micro.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.LocalDateTime;

/**
 * 抗生素字典
 */
@TableName("lis_antibiotic")
public class Antibiotic {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String antibioticCode;
    private String antibioticName;
    /** β-内酰胺类/氨基糖苷类... */
    private String category;
    private String unit;
    private Integer status;
    private LocalDateTime createTime;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getAntibioticCode() {
        return antibioticCode;
    }

    public void setAntibioticCode(String antibioticCode) {
        this.antibioticCode = antibioticCode;
    }

    public String getAntibioticName() {
        return antibioticName;
    }

    public void setAntibioticName(String antibioticName) {
        this.antibioticName = antibioticName;
    }

    public String getCategory() {
        return category;
    }

    public void setCategory(String category) {
        this.category = category;
    }

    public String getUnit() {
        return unit;
    }

    public void setUnit(String unit) {
        this.unit = unit;
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
