package com.lis.base.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.io.Serializable;

/**
 * 仪器项目映射
 */
@TableName("lis_instrument_item_map")
public class InstrumentItemMap implements Serializable {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    /** 仪器ID */
    private Long instrumentId;

    /** 检验项目ID */
    private Long testItemId;

    /** 仪器项目编码 */
    private String instrumentItemCode;

    /** 仪器项目名称 */
    private String instrumentItemName;

    /** 仪器通道码(用于仪器对接) */
    private String channelCode;

    /** 适用标本类型 (NULL表示全部) */
    private String specimenType;

    /** 操作人员ID */
    private Long operatorId;

    /** 操作人员姓名 */
    private String operatorName;

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

    public Long getTestItemId() {
        return testItemId;
    }

    public void setTestItemId(Long testItemId) {
        this.testItemId = testItemId;
    }

    public String getInstrumentItemCode() {
        return instrumentItemCode;
    }

    public void setInstrumentItemCode(String instrumentItemCode) {
        this.instrumentItemCode = instrumentItemCode;
    }

    public String getInstrumentItemName() {
        return instrumentItemName;
    }

    public void setInstrumentItemName(String instrumentItemName) {
        this.instrumentItemName = instrumentItemName;
    }

    public String getChannelCode() {
        return channelCode;
    }

    public void setChannelCode(String channelCode) {
        this.channelCode = channelCode;
    }

    public String getSpecimenType() {
        return specimenType;
    }

    public void setSpecimenType(String specimenType) {
        this.specimenType = specimenType;
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

}
