package com.lis.report.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;

@TableName("lis_micro_report_subculture")
public class MicroReportSubculture {
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;
    private Long microReportId;
    private LocalDateTime subcultureTime;
    private String medium;
    private String incubatorNo;
    private String result;
    private String operator;
    private String remark;
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

    public String getResult() {
        return result;
    }

    public void setResult(String result) {
        this.result = result;
    }

    public String getOperator() {
        return operator;
    }

    public void setOperator(String operator) {
        this.operator = operator;
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
