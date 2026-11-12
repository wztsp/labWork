package com.lis.micro.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.LocalDateTime;

/**
 * 特殊耐药标记/危急值规则
 */
@TableName("lis_resistance_rule")
public class ResistanceRule {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String organismCode;
    /** JSON 数组: 耐药抗生素 */
    private String antibioticCodes;
    /** JSON 数组: 可应用细菌编码(可多选), 空="适用于所有细菌" */
    private String bacteriaCodes;
    /** 规则名称: MRSA/CRE/CRAB/耐药规则名 */
    private String ruleName;
    /** 规则类型: 表型推断/天然耐药/获得性耐药/危急值/可配置规则 */
    private String ruleType;
    /** 期望结果(精确匹配模式用): S/I/R */
    private String result;
    /** 匹配模式: ANY_RESISTANT=所选抗生素任一耐药即命中; EXACT=任一所选抗生素结果==result 命中 */
    private String matchMode;
    /** 是否触发危急值 */
    private Integer isCritical;
    private String alertMessage;
    private String description;
    /** 系统内置规则: 1是 0否(用户自定义) */
    private Integer isBuiltin;
    private Integer status;
    private LocalDateTime createTime;
    // ====== 非持久化: 展示用 ======
    /** 细菌名称(冗余, 展示用) */
    @TableField(exist = false)
    private String bacteriaNames;
    /** 抗生素名称(冗余, 展示用) */
    @TableField(exist = false)
    private String antibioticNames;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getOrganismCode() {
        return organismCode;
    }

    public void setOrganismCode(String organismCode) {
        this.organismCode = organismCode;
    }

    public String getAntibioticCodes() {
        return antibioticCodes;
    }

    public void setAntibioticCodes(String antibioticCodes) {
        this.antibioticCodes = antibioticCodes;
    }

    public String getBacteriaCodes() {
        return bacteriaCodes;
    }

    public void setBacteriaCodes(String bacteriaCodes) {
        this.bacteriaCodes = bacteriaCodes;
    }

    public String getRuleType() {
        return ruleType;
    }

    public void setRuleType(String ruleType) {
        this.ruleType = ruleType;
    }

    public String getResult() {
        return result;
    }

    public void setResult(String result) {
        this.result = result;
    }

    public String getMatchMode() {
        return matchMode;
    }

    public void setMatchMode(String matchMode) {
        this.matchMode = matchMode;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public Integer getIsBuiltin() {
        return isBuiltin;
    }

    public void setIsBuiltin(Integer isBuiltin) {
        this.isBuiltin = isBuiltin;
    }

    public String getBacteriaNames() {
        return bacteriaNames;
    }

    public void setBacteriaNames(String bacteriaNames) {
        this.bacteriaNames = bacteriaNames;
    }

    public String getAntibioticNames() {
        return antibioticNames;
    }

    public void setAntibioticNames(String antibioticNames) {
        this.antibioticNames = antibioticNames;
    }

    public String getRuleName() {
        return ruleName;
    }

    public void setRuleName(String ruleName) {
        this.ruleName = ruleName;
    }

    public Integer getIsCritical() {
        return isCritical;
    }

    public void setIsCritical(Integer isCritical) {
        this.isCritical = isCritical;
    }

    public String getAlertMessage() {
        return alertMessage;
    }

    public void setAlertMessage(String alertMessage) {
        this.alertMessage = alertMessage;
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
