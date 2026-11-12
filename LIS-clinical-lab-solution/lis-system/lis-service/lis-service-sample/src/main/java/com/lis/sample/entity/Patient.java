package com.lis.sample.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.io.Serializable;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 患者
 */
@TableName("lis_patient")
public class Patient implements Serializable {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    /** 患者编号(HIS同步) */
    private String patientNo;

    /** 身份证号(AES加密) */
    private String idCardEnc;

    /** 身份证号哈希(用于检索) */
    private String idCardHash;

    /** 姓名 */
    private String name;

    /** 性别: 0未知 1男 2女 */
    private Integer gender;

    /** 出生日期 */
    private LocalDate birthDate;

    /** 年龄 */
    private Integer age;

    /** 手机号(AES加密) */
    private String phoneEnc;

    /** 手机号哈希(用于检索) */
    private String phoneHash;

    /** 地址 */
    private String address;

    /** 创建时间 */
    private LocalDateTime createTime;

    /** 更新时间 */
    private LocalDateTime updateTime;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getPatientNo() {
        return patientNo;
    }

    public void setPatientNo(String patientNo) {
        this.patientNo = patientNo;
    }

    public String getIdCardEnc() {
        return idCardEnc;
    }

    public void setIdCardEnc(String idCardEnc) {
        this.idCardEnc = idCardEnc;
    }

    public String getIdCardHash() {
        return idCardHash;
    }

    public void setIdCardHash(String idCardHash) {
        this.idCardHash = idCardHash;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public Integer getGender() {
        return gender;
    }

    public void setGender(Integer gender) {
        this.gender = gender;
    }

    public LocalDate getBirthDate() {
        return birthDate;
    }

    public void setBirthDate(LocalDate birthDate) {
        this.birthDate = birthDate;
    }

    public Integer getAge() {
        return age;
    }

    public void setAge(Integer age) {
        this.age = age;
    }

    public String getPhoneEnc() {
        return phoneEnc;
    }

    public void setPhoneEnc(String phoneEnc) {
        this.phoneEnc = phoneEnc;
    }

    public String getPhoneHash() {
        return phoneHash;
    }

    public void setPhoneHash(String phoneHash) {
        this.phoneHash = phoneHash;
    }

    public String getAddress() {
        return address;
    }

    public void setAddress(String address) {
        this.address = address;
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
