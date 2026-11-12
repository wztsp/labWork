package com.lis.report.dto;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 危急值处理记录操作 DTO (通知/取消通知/修改)
 */
@Data
public class CriticalRecordDTO {

    /** 危急值通知人 */
    private String notifier;

    /** 通知时间 */
    private LocalDateTime notifyTime;

    /** 发送人 */
    private String sender;

    /** 发送时间 */
    private LocalDateTime sendTime;

    /** 备注 */
    private String remark;
}