package com.lis.report.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.LocalDateTime;

/**
 * 工作站签到记录 (仪器签到 / 自动审核签到)
 * 对应表 lis_station_signin, 建表脚本见 docs/database/add-technician-signin-print.sql
 */
@TableName("lis_station_signin")
public class StationSignin {

    /** 签到类型: 仪器工作站 */
    public static final String TYPE_INSTRUMENT = "INSTRUMENT";
    /** 签到类型: 自动审核 */
    public static final String TYPE_AUTO_AUDIT = "AUTO_AUDIT";

    /** 动作: 签到 */
    public static final String ACTION_SIGNIN = "signin";
    /** 动作: 签退 */
    public static final String ACTION_SIGNOUT = "signout";

    /** 状态: 在岗 */
    public static final int STATUS_ON_DUTY = 1;
    /** 状态: 已签退 */
    public static final int STATUS_OFF_DUTY = 0;

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 签到类型: INSTRUMENT 仪器 / AUTO_AUDIT 自动审核 */
    private String signinType;

    /** 仪器ID (AUTO_AUDIT 时为 null) */
    private Long instrumentId;

    /** 操作人ID */
    private Long operatorId;

    /** 操作人姓名 */
    private String operatorName;

    /** 动作: signin 签到 / signout 签退 */
    private String action;

    /** 签到时间 */
    private LocalDateTime signinTime;

    /** 签退时间 */
    private LocalDateTime signoutTime;

    /** 状态: 1 在岗 / 0 已签退 */
    private Integer status;

    /** 备注 */
    private String remark;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getSigninType() {
        return signinType;
    }

    public void setSigninType(String signinType) {
        this.signinType = signinType;
    }

    public Long getInstrumentId() {
        return instrumentId;
    }

    public void setInstrumentId(Long instrumentId) {
        this.instrumentId = instrumentId;
    }

    public Long getOperatorId() {
        return operatorId;
    }

    public void setOperatorId(Long operatorId) {
        this.operatorId = operatorId;
    }

    public String getOperatorName() {
        return operatorName;
    }

    public void setOperatorName(String operatorName) {
        this.operatorName = operatorName;
    }

    public String getAction() {
        return action;
    }

    public void setAction(String action) {
        this.action = action;
    }

    public LocalDateTime getSigninTime() {
        return signinTime;
    }

    public void setSigninTime(LocalDateTime signinTime) {
        this.signinTime = signinTime;
    }

    public LocalDateTime getSignoutTime() {
        return signoutTime;
    }

    public void setSignoutTime(LocalDateTime signoutTime) {
        this.signoutTime = signoutTime;
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
