package com.lis.sample.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 条码生成规则实体
 * 支持多种格式: 纯数字 / 年月日+数字 / 2位年+月日+数字 / 字母+数字 等
 */
@TableName("lis_barcode_rule")
public class BarcodeRule implements Serializable {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    /** 规则编码 (业务类型: SAMPLE标本条码 / ORDER申请号 / VISIT就诊号 / PATIENT患者号) */
    private String ruleCode;

    /** 规则名称 */
    private String ruleName;

    /** 前缀 (字母, 可为空, 如 S/V/O/P) */
    private String prefix;

    /**
     * 日期格式:
     * none       - 无日期
     * yyyyMMdd   - 完整年月日 (如 20260731)
     * yyMMdd     - 2位年+月日 (如 260731)
     * yyyyMM     - 年月 (如 202607)
     * yyyy       - 年 (如 2026)
     */
    private String datePattern;

    /** 序列号长度 (不足补0, 如 3→001, 6→000001) */
    private Integer seqLength;

    /**
     * 序列号策略:
     * daily  - 每日重置 (按天从1开始)
     * global - 全局递增 (不重置)
     */
    private String seqStrategy;

    /** 是否启用: 1启用 0停用 */
    private Integer enabled;

    /** 备注 (含示例) */
    private String remark;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getRuleCode() {
        return ruleCode;
    }

    public void setRuleCode(String ruleCode) {
        this.ruleCode = ruleCode;
    }

    public String getRuleName() {
        return ruleName;
    }

    public void setRuleName(String ruleName) {
        this.ruleName = ruleName;
    }

    public String getPrefix() {
        return prefix;
    }

    public void setPrefix(String prefix) {
        this.prefix = prefix;
    }

    public String getDatePattern() {
        return datePattern;
    }

    public void setDatePattern(String datePattern) {
        this.datePattern = datePattern;
    }

    public Integer getSeqLength() {
        return seqLength;
    }

    public void setSeqLength(Integer seqLength) {
        this.seqLength = seqLength;
    }

    public String getSeqStrategy() {
        return seqStrategy;
    }

    public void setSeqStrategy(String seqStrategy) {
        this.seqStrategy = seqStrategy;
    }

    public Integer getEnabled() {
        return enabled;
    }

    public void setEnabled(Integer enabled) {
        this.enabled = enabled;
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

    public LocalDateTime getUpdateTime() {
        return updateTime;
    }

    public void setUpdateTime(LocalDateTime updateTime) {
        this.updateTime = updateTime;
    }

}
