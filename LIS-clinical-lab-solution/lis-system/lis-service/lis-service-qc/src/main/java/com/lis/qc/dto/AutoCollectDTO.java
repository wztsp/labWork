package com.lis.qc.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 仪器自动推送的质控数据
 */
@Data
public class AutoCollectDTO {

    /** 仪器ID */
    private Long instrumentId;

    /** 质控品批号ID */
    private Long lotId;

    /** 实测值 */
    private BigDecimal value;

    /** 测量时间 (为空时取当前时间) */
    private LocalDateTime testTime;

    /** 操作人ID (可选) */
    private Long operatorId;
}
