package com.lis.reagent.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 供应商主数据（通用表，非试剂专属，仪器模块未来可复用）。
 *
 * <p>表名 {@code lis_supplier} 不带 reagent 前缀（主理人 Q1 裁决）。</p>
 *
 * <p>手写 getter/setter，不使用 Lombok（C-10）。不声明 {@code deleted} 字段（C-9 复核结论）。</p>
 */
@TableName("lis_supplier")
public class Supplier {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 供应商编码，唯一 */
    private String supplierCode;
    /** 供应商名称 */
    private String supplierName;
    /** 简称 */
    private String shortName;
    /** 统一社会信用代码 */
    private String creditCode;
    /** 联系人 */
    private String contactPerson;
    private String phone;
    private String email;
    private String address;
    /** 营业执照号 */
    private String licenseNo;
    /** 营业执照到期日 */
    private LocalDate licenseExpiry;
    /** 医疗器械经营许可证号 */
    private String medicalCertNo;
    /** 医疗器械经营许可证到期日 */
    private LocalDate medicalCertExpiry;
    /** 资质状态 0待完善 1有效 2已过期，见 {@code com.lis.reagent.enums.QualStatus} */
    private Integer qualStatus;
    /** 综合评分（第二批 R-11 使用） */
    private BigDecimal score;
    /** 1手工录入 2存量字符串迁移生成（草稿） */
    private Integer sourceType;
    /** 0停用 1启用 */
    private Integer status;
    private String remark;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getSupplierCode() {
        return supplierCode;
    }

    public void setSupplierCode(String supplierCode) {
        this.supplierCode = supplierCode;
    }

    public String getSupplierName() {
        return supplierName;
    }

    public void setSupplierName(String supplierName) {
        this.supplierName = supplierName;
    }

    public String getShortName() {
        return shortName;
    }

    public void setShortName(String shortName) {
        this.shortName = shortName;
    }

    public String getCreditCode() {
        return creditCode;
    }

    public void setCreditCode(String creditCode) {
        this.creditCode = creditCode;
    }

    public String getContactPerson() {
        return contactPerson;
    }

    public void setContactPerson(String contactPerson) {
        this.contactPerson = contactPerson;
    }

    public String getPhone() {
        return phone;
    }

    public void setPhone(String phone) {
        this.phone = phone;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getAddress() {
        return address;
    }

    public void setAddress(String address) {
        this.address = address;
    }

    public String getLicenseNo() {
        return licenseNo;
    }

    public void setLicenseNo(String licenseNo) {
        this.licenseNo = licenseNo;
    }

    public LocalDate getLicenseExpiry() {
        return licenseExpiry;
    }

    public void setLicenseExpiry(LocalDate licenseExpiry) {
        this.licenseExpiry = licenseExpiry;
    }

    public String getMedicalCertNo() {
        return medicalCertNo;
    }

    public void setMedicalCertNo(String medicalCertNo) {
        this.medicalCertNo = medicalCertNo;
    }

    public LocalDate getMedicalCertExpiry() {
        return medicalCertExpiry;
    }

    public void setMedicalCertExpiry(LocalDate medicalCertExpiry) {
        this.medicalCertExpiry = medicalCertExpiry;
    }

    public Integer getQualStatus() {
        return qualStatus;
    }

    public void setQualStatus(Integer qualStatus) {
        this.qualStatus = qualStatus;
    }

    public BigDecimal getScore() {
        return score;
    }

    public void setScore(BigDecimal score) {
        this.score = score;
    }

    public Integer getSourceType() {
        return sourceType;
    }

    public void setSourceType(Integer sourceType) {
        this.sourceType = sourceType;
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

    public LocalDateTime getUpdateTime() {
        return updateTime;
    }

    public void setUpdateTime(LocalDateTime updateTime) {
        this.updateTime = updateTime;
    }

}
