package com.lis.report.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.LocalDateTime;

/**
 * 检验报告实体
 */
@TableName("lis_report")
public class Report {

    /** 报告状态: 草稿 */
    public static final int STATUS_DRAFT = 0;
    /** 报告状态: 已审核 */
    public static final int STATUS_AUDITED = 1;
    /** 报告状态: 已发布 */
    public static final int STATUS_PUBLISHED = 2;
    /** 报告状态: 已修改 */
    public static final int STATUS_MODIFIED = 3;

    @TableId(type = IdType.AUTO)
    private Long id;

    private String reportNo;

    private Long sampleId;

    private Long patientId;

    private String patientName;

    private Integer reportType;

    /** 状态: 0=草稿 1=已审核 2=已发布 3=已修改 */
    private Integer status;

    private Long auditorId;

    private LocalDateTime auditTime;

    private Long publisherId;

    private LocalDateTime publishTime;

    private String reportDoctor;

    private String checker;

    private String pdfPath;

    /** HIS同步状态: 0=未同步 1=已同步 2=同步失败 */
    private Integer hisSyncStatus;

    private LocalDateTime hisSyncTime;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getReportNo() {
        return reportNo;
    }

    public void setReportNo(String reportNo) {
        this.reportNo = reportNo;
    }

    public Long getSampleId() {
        return sampleId;
    }

    public void setSampleId(Long sampleId) {
        this.sampleId = sampleId;
    }

    public Long getPatientId() {
        return patientId;
    }

    public void setPatientId(Long patientId) {
        this.patientId = patientId;
    }

    public String getPatientName() {
        return patientName;
    }

    public void setPatientName(String patientName) {
        this.patientName = patientName;
    }

    public Integer getReportType() {
        return reportType;
    }

    public void setReportType(Integer reportType) {
        this.reportType = reportType;
    }

    public Integer getStatus() {
        return status;
    }

    public void setStatus(Integer status) {
        this.status = status;
    }

    public Long getAuditorId() {
        return auditorId;
    }

    public void setAuditorId(Long auditorId) {
        this.auditorId = auditorId;
    }

    public LocalDateTime getAuditTime() {
        return auditTime;
    }

    public void setAuditTime(LocalDateTime auditTime) {
        this.auditTime = auditTime;
    }

    public Long getPublisherId() {
        return publisherId;
    }

    public void setPublisherId(Long publisherId) {
        this.publisherId = publisherId;
    }

    public LocalDateTime getPublishTime() {
        return publishTime;
    }

    public void setPublishTime(LocalDateTime publishTime) {
        this.publishTime = publishTime;
    }

    public String getReportDoctor() {
        return reportDoctor;
    }

    public void setReportDoctor(String reportDoctor) {
        this.reportDoctor = reportDoctor;
    }

    public String getChecker() {
        return checker;
    }

    public void setChecker(String checker) {
        this.checker = checker;
    }

    public String getPdfPath() {
        return pdfPath;
    }

    public void setPdfPath(String pdfPath) {
        this.pdfPath = pdfPath;
    }

    public Integer getHisSyncStatus() {
        return hisSyncStatus;
    }

    public void setHisSyncStatus(Integer hisSyncStatus) {
        this.hisSyncStatus = hisSyncStatus;
    }

    public LocalDateTime getHisSyncTime() {
        return hisSyncTime;
    }

    public void setHisSyncTime(LocalDateTime hisSyncTime) {
        this.hisSyncTime = hisSyncTime;
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
