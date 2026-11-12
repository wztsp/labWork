package com.lis.monolith.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * TAT 超时规则
 */
@TableName("lis_tat_rule")
public class TatRule implements Serializable {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 标本类型 */
    private String specimenType;

    /** 检验项目ID */
    private Long testItemId;

    /** 检验项目名称 (冗余) */
    private String testItemName;

    /** 急查超时时间(分钟) */
    private Integer urgentTimeout;

    /** 普通超时时间(分钟) */
    private Integer normalTimeout;

    /** 0停用 1启用 */
    private Integer enabled;

    private String remark;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getSpecimenType() { return specimenType; }
    public void setSpecimenType(String specimenType) { this.specimenType = specimenType; }
    public Long getTestItemId() { return testItemId; }
    public void setTestItemId(Long testItemId) { this.testItemId = testItemId; }
    public String getTestItemName() { return testItemName; }
    public void setTestItemName(String testItemName) { this.testItemName = testItemName; }
    public Integer getUrgentTimeout() { return urgentTimeout; }
    public void setUrgentTimeout(Integer urgentTimeout) { this.urgentTimeout = urgentTimeout; }
    public Integer getNormalTimeout() { return normalTimeout; }
    public void setNormalTimeout(Integer normalTimeout) { this.normalTimeout = normalTimeout; }
    public Integer getEnabled() { return enabled; }
    public void setEnabled(Integer enabled) { this.enabled = enabled; }
    public String getRemark() { return remark; }
    public void setRemark(String remark) { this.remark = remark; }
    public LocalDateTime getCreateTime() { return createTime; }
    public void setCreateTime(LocalDateTime createTime) { this.createTime = createTime; }
    public LocalDateTime getUpdateTime() { return updateTime; }
    public void setUpdateTime(LocalDateTime updateTime) { this.updateTime = updateTime; }
}