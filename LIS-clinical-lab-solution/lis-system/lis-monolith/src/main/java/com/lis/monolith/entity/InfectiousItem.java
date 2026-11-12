package com.lis.monolith.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 传染病检验项目配置 (阳性时发送传染病消息)
 */
@TableName("lis_infectious_item")
public class InfectiousItem implements Serializable {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long testItemId;

    private String testItemName;

    /** 判定阳性的文本, 默认"阳性" */
    private String positiveText;

    /** 0停用 1启用 */
    private Integer enabled;

    private String remark;

    private LocalDateTime createTime;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getTestItemId() { return testItemId; }
    public void setTestItemId(Long testItemId) { this.testItemId = testItemId; }
    public String getTestItemName() { return testItemName; }
    public void setTestItemName(String testItemName) { this.testItemName = testItemName; }
    public String getPositiveText() { return positiveText; }
    public void setPositiveText(String positiveText) { this.positiveText = positiveText; }
    public Integer getEnabled() { return enabled; }
    public void setEnabled(Integer enabled) { this.enabled = enabled; }
    public String getRemark() { return remark; }
    public void setRemark(String remark) { this.remark = remark; }
    public LocalDateTime getCreateTime() { return createTime; }
    public void setCreateTime(LocalDateTime createTime) { this.createTime = createTime; }
}