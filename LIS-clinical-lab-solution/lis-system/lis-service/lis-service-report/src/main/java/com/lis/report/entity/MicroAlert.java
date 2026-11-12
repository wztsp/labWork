package com.lis.report.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.LocalDateTime;

/**
 * 微生物特殊耐药/危急值预警
 */
@TableName("lis_micro_alert")
public class MicroAlert {

    /** 预警级别: WARNING / CRITICAL */
    public static final String LEVEL_WARNING = "WARNING";
    public static final String LEVEL_CRITICAL = "CRITICAL";

    /** 状态: PENDING(待处理) / READ(已读) / HANDLED(已处理) */
    public static final String STATUS_PENDING = "PENDING";
    public static final String STATUS_READ = "READ";
    public static final String STATUS_HANDLED = "HANDLED";

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 关联微生物报告ID */
    private Long microReportId;

    /** 关联细菌鉴定ID */
    private Long organismId;

    /** 预警类型: MRSA/VRE/CRE/ESBL/MDR/CRITICAL_VALUE */
    private String alertType;

    /** 预警级别: WARNING/CRITICAL */
    private String alertLevel;

    /** 细菌名称 */
    private String organismName;

    /** 预警描述 */
    private String message;

    /** 处理状态 */
    private String status;

    /** 处理人 */
    private String handler;

    /** 处理备注 */
    private String handleRemark;

    /** 处理时间 */
    private LocalDateTime handleTime;

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

    public Long getOrganismId() {
        return organismId;
    }

    public void setOrganismId(Long organismId) {
        this.organismId = organismId;
    }

    public String getAlertType() {
        return alertType;
    }

    public void setAlertType(String alertType) {
        this.alertType = alertType;
    }

    public String getAlertLevel() {
        return alertLevel;
    }

    public void setAlertLevel(String alertLevel) {
        this.alertLevel = alertLevel;
    }

    public String getOrganismName() {
        return organismName;
    }

    public void setOrganismName(String organismName) {
        this.organismName = organismName;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getHandler() {
        return handler;
    }

    public void setHandler(String handler) {
        this.handler = handler;
    }

    public String getHandleRemark() {
        return handleRemark;
    }

    public void setHandleRemark(String handleRemark) {
        this.handleRemark = handleRemark;
    }

    public LocalDateTime getHandleTime() {
        return handleTime;
    }

    public void setHandleTime(LocalDateTime handleTime) {
        this.handleTime = handleTime;
    }

    public LocalDateTime getCreateTime() {
        return createTime;
    }

    public void setCreateTime(LocalDateTime createTime) {
        this.createTime = createTime;
    }

}
