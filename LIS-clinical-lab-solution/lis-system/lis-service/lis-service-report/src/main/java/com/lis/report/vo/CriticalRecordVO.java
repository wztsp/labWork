package com.lis.report.vo;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 危急值列表视图: 危急结果 + 处理记录(通知人/通知时间/发送人/发送时间/备注/状态)
 */
@Data
public class CriticalRecordVO {

    /** 结果ID (lis_result.id) */
    private Long id;

    private Long resultId;

    private Long sampleId;

    private Long testItemId;

    private String patientName;

    private String testItemName;

    private String resultValue;

    private String unit;

    private String refRange;

    private String criticalLow;

    private String criticalHigh;

    private Integer isCritical;

    /** 处理记录ID (lis_critical_record.id), 为空表示尚未通知 */
    private Long recordId;

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

    /** 状态: 0待通知 1已通知 2已取消 */
    private Integer status;

    private LocalDateTime createTime;
}