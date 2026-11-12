package com.lis.base.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 检验试管字典
 */
@TableName("lis_test_tube")
public class TestTube implements Serializable {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    /** 管编码 */
    private String tubeCode;

    /** 管名称 */
    private String tubeName;

    /** 管颜色: 红/紫/蓝/绿/黑/黄/灰 */
    private String tubeColor;

    /** 管类型: 采血管/尿杯/粪盒/培养管 */
    private String tubeType;

    /** 容量 */
    private String volume;

    /** 抗凝剂/添加剂 */
    private String additive;

    /** 排序 */
    private Integer sortOrder;

    /** 状态: 0停用 1启用 */
    private Integer status;

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

    public String getTubeCode() {
        return tubeCode;
    }

    public void setTubeCode(String tubeCode) {
        this.tubeCode = tubeCode;
    }

    public String getTubeName() {
        return tubeName;
    }

    public void setTubeName(String tubeName) {
        this.tubeName = tubeName;
    }

    public String getTubeColor() {
        return tubeColor;
    }

    public void setTubeColor(String tubeColor) {
        this.tubeColor = tubeColor;
    }

    public String getTubeType() {
        return tubeType;
    }

    public void setTubeType(String tubeType) {
        this.tubeType = tubeType;
    }

    public String getVolume() {
        return volume;
    }

    public void setVolume(String volume) {
        this.volume = volume;
    }

    public String getAdditive() {
        return additive;
    }

    public void setAdditive(String additive) {
        this.additive = additive;
    }

    public Integer getSortOrder() {
        return sortOrder;
    }

    public void setSortOrder(Integer sortOrder) {
        this.sortOrder = sortOrder;
    }

    public Integer getStatus() {
        return status;
    }

    public void setStatus(Integer status) {
        this.status = status;
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
