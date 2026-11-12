package com.lis.base.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.io.Serializable;

/**
 * 仪器-标本类型关联表
 */
@TableName("lis_instrument_specimen_map")
public class InstrumentSpecimenMap implements Serializable {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    /** 仪器ID */
    private Long instrumentId;

    /** 标本类型ID */
    private Long specimenTypeId;

    /** 标本类型名称（冗余） */
    private String specimenTypeName;

    /** 标本类型编码（冗余） */
    private String specimenTypeCode;

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

    public Long getSpecimenTypeId() {
        return specimenTypeId;
    }

    public void setSpecimenTypeId(Long specimenTypeId) {
        this.specimenTypeId = specimenTypeId;
    }

    public String getSpecimenTypeName() {
        return specimenTypeName;
    }

    public void setSpecimenTypeName(String specimenTypeName) {
        this.specimenTypeName = specimenTypeName;
    }

    public String getSpecimenTypeCode() {
        return specimenTypeCode;
    }

    public void setSpecimenTypeCode(String specimenTypeCode) {
        this.specimenTypeCode = specimenTypeCode;
    }

}
