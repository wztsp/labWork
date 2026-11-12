package com.lis.base.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 检验项目字典
 */
@TableName("lis_test_item")
public class TestItem implements Serializable {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    /** 项目编码 */
    private String itemCode;

    /** 项目名称 */
    private String itemName;

    /** 项目英文名 */
    private String itemNameEn;

    /** 缩写 */
    private String shortName;

    /** 项目分类 */
    private String category;

    /** 标本类型 */
    private String specimenType;

    /** 单位 */
    private String unit;

    /** 检测方法 */
    private String method;

    /** 是否检验项目: 0否 1是 */
    private Integer isPanel;

    /** 父项目ID(检验项目) */
    private Long parentId;

    /** 价格 */
    private BigDecimal price;

    /** 绑定的检验试管ID (NULL表示未绑定) */
    private Long tubeId;

    /** 采集方式ID (关联 lis_collection_method) */
    private Long collectionMethodId;

    /** 报告模板ID (关联 lis_report_template) */
    private Long reportTemplateId;

    /** 状态: 0停用 1启用 */
    private Integer status;

    /** 创建时间 */
    private LocalDateTime createTime;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getItemCode() {
        return itemCode;
    }

    public void setItemCode(String itemCode) {
        this.itemCode = itemCode;
    }

    public String getItemName() {
        return itemName;
    }

    public void setItemName(String itemName) {
        this.itemName = itemName;
    }

    public String getItemNameEn() {
        return itemNameEn;
    }

    public void setItemNameEn(String itemNameEn) {
        this.itemNameEn = itemNameEn;
    }

    public String getShortName() {
        return shortName;
    }

    public void setShortName(String shortName) {
        this.shortName = shortName;
    }

    public String getCategory() {
        return category;
    }

    public void setCategory(String category) {
        this.category = category;
    }

    public String getSpecimenType() {
        return specimenType;
    }

    public void setSpecimenType(String specimenType) {
        this.specimenType = specimenType;
    }

    public String getUnit() {
        return unit;
    }

    public void setUnit(String unit) {
        this.unit = unit;
    }

    public String getMethod() {
        return method;
    }

    public void setMethod(String method) {
        this.method = method;
    }

    public Integer getIsPanel() {
        return isPanel;
    }

    public void setIsPanel(Integer isPanel) {
        this.isPanel = isPanel;
    }

    public Long getParentId() {
        return parentId;
    }

    public void setParentId(Long parentId) {
        this.parentId = parentId;
    }

    public BigDecimal getPrice() {
        return price;
    }

    public void setPrice(BigDecimal price) {
        this.price = price;
    }

    public Long getTubeId() {
        return tubeId;
    }

    public void setTubeId(Long tubeId) {
        this.tubeId = tubeId;
    }

    public Long getCollectionMethodId() {
        return collectionMethodId;
    }

    public void setCollectionMethodId(Long collectionMethodId) {
        this.collectionMethodId = collectionMethodId;
    }

    public Long getReportTemplateId() {
        return reportTemplateId;
    }

    public void setReportTemplateId(Long reportTemplateId) {
        this.reportTemplateId = reportTemplateId;
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
