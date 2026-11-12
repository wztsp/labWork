package com.lis.micro.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.LocalDateTime;

/**
 * 细菌字典
 */
@TableName("lis_bacteria")
public class Bacteria {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 细菌编码，如 ECO */
    private String bacteriaCode;
    /** 中文名称，如 大肠埃希菌 */
    private String bacteriaName;
    /** 拉丁学名，如 Escherichia coli */
    private String bacteriaNameLatin;
    /** 革兰氏属性：G+/G-/真菌/抗酸菌 */
    private String gramType;
    /** 菌属分类：肠杆菌科/非发酵菌/葡萄球菌属/链球菌属/肠球菌属/弧菌科/厌氧菌/分枝杆菌/真菌/其他 */
    private String bacteriaCategory;
    /** 形态：球菌/杆菌/球杆菌/弯曲杆菌/酵母样/丝状真菌 */
    private String morphology;
    /** 是否常见菌：1是 0否 */
    private Integer isCommon;
    private Integer status;
    private LocalDateTime createTime;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getBacteriaCode() {
        return bacteriaCode;
    }

    public void setBacteriaCode(String bacteriaCode) {
        this.bacteriaCode = bacteriaCode;
    }

    public String getBacteriaName() {
        return bacteriaName;
    }

    public void setBacteriaName(String bacteriaName) {
        this.bacteriaName = bacteriaName;
    }

    public String getBacteriaNameLatin() {
        return bacteriaNameLatin;
    }

    public void setBacteriaNameLatin(String bacteriaNameLatin) {
        this.bacteriaNameLatin = bacteriaNameLatin;
    }

    public String getGramType() {
        return gramType;
    }

    public void setGramType(String gramType) {
        this.gramType = gramType;
    }

    public String getBacteriaCategory() {
        return bacteriaCategory;
    }

    public void setBacteriaCategory(String bacteriaCategory) {
        this.bacteriaCategory = bacteriaCategory;
    }

    public String getMorphology() {
        return morphology;
    }

    public void setMorphology(String morphology) {
        this.morphology = morphology;
    }

    public Integer getIsCommon() {
        return isCommon;
    }

    public void setIsCommon(Integer isCommon) {
        this.isCommon = isCommon;
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
