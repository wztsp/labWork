package com.lis.report.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.LocalDateTime;

/**
 * 微生物细菌鉴定实体
 */
@TableName("lis_micro_organism")
public class MicroOrganism {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    /** 微生物报告ID */
    private Long microReportId;

    /** 细菌名称 */
    private String organismName;

    /** 细菌编码 */
    private String organismCode;

    /** 菌落数量 */
    private String quantity;

    /** 培养方法 (数据库列名 culture_method) */
    @TableField("culture_method")
    private String colonsMethod;

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

    public String getOrganismName() {
        return organismName;
    }

    public void setOrganismName(String organismName) {
        this.organismName = organismName;
    }

    public String getOrganismCode() {
        return organismCode;
    }

    public void setOrganismCode(String organismCode) {
        this.organismCode = organismCode;
    }

    public String getQuantity() {
        return quantity;
    }

    public void setQuantity(String quantity) {
        this.quantity = quantity;
    }

    public String getColonsMethod() {
        return colonsMethod;
    }

    public void setColonsMethod(String colonsMethod) {
        this.colonsMethod = colonsMethod;
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
