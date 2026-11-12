package com.lis.reagent.dto;

import java.io.Serializable;
import java.time.LocalDate;

/**
 * 供应商创建 / 编辑入参。
 *
 * @author LIS Team
 */
public class SupplierCreateDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 供应商 ID，新建时为 null */
    private Long id;
    /** 供应商编码；为空时由 Service 生成，唯一 */
    private String supplierCode;
    /** 供应商名称，必填 */
    private String supplierName;
    /** 简称 */
    private String shortName;
    /** 统一社会信用代码 */
    private String creditCode;
    /** 联系人 */
    private String contactPerson;
    /** 联系电话 */
    private String phone;
    /** 邮箱 */
    private String email;
    /** 地址 */
    private String address;
    /** 营业执照号 */
    private String licenseNo;
    /** 营业执照有效期 */
    private LocalDate licenseExpiry;
    /** 医疗器械经营许可证号 */
    private String medicalCertNo;
    /** 医疗器械经营许可证有效期 */
    private LocalDate medicalCertExpiry;
    /** 状态：0停用 1启用 */
    private Integer status;
    /** 备注 */
    private String remark;

    public SupplierCreateDTO() {
    }

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
}
