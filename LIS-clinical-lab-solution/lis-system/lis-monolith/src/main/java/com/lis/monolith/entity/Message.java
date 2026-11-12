package com.lis.monolith.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 消息提醒 (危急值 / 传染病 / TAT超时)
 */
@TableName("lis_message")
public class Message implements Serializable {

    /** 危急值 */
    public static final String TYPE_CRITICAL = "CRITICAL";
    /** 传染病 */
    public static final String TYPE_INFECTIOUS = "INFECTIOUS";
    /** TAT超时 */
    public static final String TYPE_TAT = "TAT";

    @TableId(type = IdType.AUTO)
    private Long id;

    /** CRITICAL / INFECTIOUS / TAT */
    private String messageType;

    private String title;

    private String content;

    /** 指定接收用户, NULL表示按科室/全员 */
    private Long userId;

    /** 指定接收科室, NULL表示全员 */
    private Long departmentId;

    /** 业务ID (resultId / sampleId) */
    private Long businessId;

    private Long sampleId;

    private Long testItemId;

    /** 0未读 1已读 */
    private Integer isRead;

    private LocalDateTime readTime;

    private LocalDateTime createTime;

    // ===== getters / setters =====
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getMessageType() { return messageType; }
    public void setMessageType(String messageType) { this.messageType = messageType; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public String getContent() { return content; }
    public void setContent(String content) { this.content = content; }
    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }
    public Long getDepartmentId() { return departmentId; }
    public void setDepartmentId(Long departmentId) { this.departmentId = departmentId; }
    public Long getBusinessId() { return businessId; }
    public void setBusinessId(Long businessId) { this.businessId = businessId; }
    public Long getSampleId() { return sampleId; }
    public void setSampleId(Long sampleId) { this.sampleId = sampleId; }
    public Long getTestItemId() { return testItemId; }
    public void setTestItemId(Long testItemId) { this.testItemId = testItemId; }
    public Integer getIsRead() { return isRead; }
    public void setIsRead(Integer isRead) { this.isRead = isRead; }
    public LocalDateTime getReadTime() { return readTime; }
    public void setReadTime(LocalDateTime readTime) { this.readTime = readTime; }
    public LocalDateTime getCreateTime() { return createTime; }
    public void setCreateTime(LocalDateTime createTime) { this.createTime = createTime; }
}