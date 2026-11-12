package com.lis.micro.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 培养基类型字典
 * 如: 血平板 / 麦康凯平板 / 巧克力平板 / SS平板 / 沙保弱平板 等
 */
@TableName("lis_medium_type")
public class MediumType implements Serializable {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 培养基编码 */
    private String mediumCode;

    /** 培养基名称 */
    private String mediumName;

    /** 培养基缩写 (打印条码用) */
    private String mediumAbbr;

    /** 备注 */
    private String remark;

    /** 状态: 0停用 1启用 */
    private Integer status;

    private LocalDateTime createTime;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getMediumCode() { return mediumCode; }
    public void setMediumCode(String mediumCode) { this.mediumCode = mediumCode; }
    public String getMediumName() { return mediumName; }
    public void setMediumName(String mediumName) { this.mediumName = mediumName; }
    public String getMediumAbbr() { return mediumAbbr; }
    public void setMediumAbbr(String mediumAbbr) { this.mediumAbbr = mediumAbbr; }
    public String getRemark() { return remark; }
    public void setRemark(String remark) { this.remark = remark; }
    public Integer getStatus() { return status; }
    public void setStatus(Integer status) { this.status = status; }
    public LocalDateTime getCreateTime() { return createTime; }
    public void setCreateTime(LocalDateTime createTime) { this.createTime = createTime; }
}