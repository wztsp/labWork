package com.lis.base.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.io.Serializable;
import java.math.BigDecimal;

/**
 * 参考范围
 */
@TableName("lis_reference_range")
public class ReferenceRange implements Serializable {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    /** 检验项目ID */
    private Long testItemId;

    /** 检验指标名称 (关联lis_test_item, 非数据库字段) */
    @TableField(exist = false)
    private String testItemName;

    /** 检验指标编码 (关联lis_test_item, 非数据库字段) */
    @TableField(exist = false)
    private String testItemCode;

    /** 性别: 0通用 1男 2女 */
    private Integer gender;

    /** 最小年龄 */
    private BigDecimal ageMin;

    /** 最大年龄 */
    private BigDecimal ageMax;

    /** 参考值下限 */
    private String refLow;

    /** 参考值上限 */
    private String refHigh;

    /** 参考值文本(非数值型) */
    private String refText;

    /** 危急值下限 */
    private String criticalLow;

    /** 危急值上限 */
    private String criticalHigh;

    /** 单位 */
    private String unit;

    /** 标本类型 (NULL表示全部适用) */
    private String specimenType;

    /** 仪器ID (NULL表示全部适用) */
    private Long instrumentId;

    /** 参考范围条件备注: 生理周期/体位/采血时间/饮食状态/孕周等 */
    private String conditionRemark;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getTestItemId() {
        return testItemId;
    }

    public void setTestItemId(Long testItemId) {
        this.testItemId = testItemId;
    }

    public String getTestItemName() {
        return testItemName;
    }

    public void setTestItemName(String testItemName) {
        this.testItemName = testItemName;
    }

    public String getTestItemCode() {
        return testItemCode;
    }

    public void setTestItemCode(String testItemCode) {
        this.testItemCode = testItemCode;
    }

    public Integer getGender() {
        return gender;
    }

    public void setGender(Integer gender) {
        this.gender = gender;
    }

    public BigDecimal getAgeMin() {
        return ageMin;
    }

    public void setAgeMin(BigDecimal ageMin) {
        this.ageMin = ageMin;
    }

    public BigDecimal getAgeMax() {
        return ageMax;
    }

    public void setAgeMax(BigDecimal ageMax) {
        this.ageMax = ageMax;
    }

    public String getRefLow() {
        return refLow;
    }

    public void setRefLow(String refLow) {
        this.refLow = refLow;
    }

    public String getRefHigh() {
        return refHigh;
    }

    public void setRefHigh(String refHigh) {
        this.refHigh = refHigh;
    }

    public String getRefText() {
        return refText;
    }

    public void setRefText(String refText) {
        this.refText = refText;
    }

    public String getCriticalLow() {
        return criticalLow;
    }

    public void setCriticalLow(String criticalLow) {
        this.criticalLow = criticalLow;
    }

    public String getCriticalHigh() {
        return criticalHigh;
    }

    public void setCriticalHigh(String criticalHigh) {
        this.criticalHigh = criticalHigh;
    }

    public String getUnit() {
        return unit;
    }

    public void setUnit(String unit) {
        this.unit = unit;
    }

    public String getSpecimenType() {
        return specimenType;
    }

    public void setSpecimenType(String specimenType) {
        this.specimenType = specimenType;
    }

    public Long getInstrumentId() {
        return instrumentId;
    }

    public void setInstrumentId(Long instrumentId) {
        this.instrumentId = instrumentId;
    }

    public String getConditionRemark() {
        return conditionRemark;
    }

    public void setConditionRemark(String conditionRemark) {
        this.conditionRemark = conditionRemark;
    }

}
