package com.lis.base.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.io.Serializable;

/**
 * 仪器-操作人员关联表
 */
@TableName("lis_instrument_staff_map")
public class InstrumentStaffMap implements Serializable {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    /** 仪器ID */
    private Long instrumentId;

    /** 操作人员ID（lis_staff.id） */
    private Long staffId;

    /** 操作人员姓名（冗余） */
    private String staffName;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getInstrumentId() {
        return instrumentId;
    }

    public void setInstrumentId(Long instrumentId) {
        this.instrumentId = instrumentId;
    }

    public Long getStaffId() {
        return staffId;
    }

    public void setStaffId(Long staffId) {
        this.staffId = staffId;
    }

    public String getStaffName() {
        return staffName;
    }

    public void setStaffName(String staffName) {
        this.staffName = staffName;
    }

}
