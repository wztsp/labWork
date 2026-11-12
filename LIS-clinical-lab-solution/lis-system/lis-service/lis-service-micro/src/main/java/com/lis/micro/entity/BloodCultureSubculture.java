package com.lis.micro.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.LocalDateTime;

/**
 * 血培养转种记录
 */
@TableName("lis_blood_culture_subculture")
public class BloodCultureSubculture {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long bloodCultureId;
    private LocalDateTime subcultureTime;
    private String medium;
    private String incubatorNo;
    private Long operatorId;
    private String result;
    private String remark;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getBloodCultureId() {
        return bloodCultureId;
    }

    public void setBloodCultureId(Long bloodCultureId) {
        this.bloodCultureId = bloodCultureId;
    }

    public LocalDateTime getSubcultureTime() {
        return subcultureTime;
    }

    public void setSubcultureTime(LocalDateTime subcultureTime) {
        this.subcultureTime = subcultureTime;
    }

    public String getMedium() {
        return medium;
    }

    public void setMedium(String medium) {
        this.medium = medium;
    }

    public String getIncubatorNo() {
        return incubatorNo;
    }

    public void setIncubatorNo(String incubatorNo) {
        this.incubatorNo = incubatorNo;
    }

    public Long getOperatorId() {
        return operatorId;
    }

    public void setOperatorId(Long operatorId) {
        this.operatorId = operatorId;
    }

    public String getResult() {
        return result;
    }

    public void setResult(String result) {
        this.result = result;
    }

    public String getRemark() {
        return remark;
    }

    public void setRemark(String remark) {
        this.remark = remark;
    }

}
