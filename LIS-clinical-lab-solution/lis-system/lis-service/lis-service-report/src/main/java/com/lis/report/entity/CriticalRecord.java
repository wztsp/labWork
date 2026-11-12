package com.lis.report.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 危急值处理记录实体
 * 一个检验结果(result)对应一条处理记录, 通知->取消->再通知 均更新同一记录
 */
@Data
@TableName("lis_critical_record")
public class CriticalRecord {

    /** 状态: 0待通知 */
    public static final int STATUS_PENDING = 0;
    /** 状态: 1已通知 */
    public static final int STATUS_NOTIFIED = 1;
    /** 状态: 2已取消 */
    public static final int STATUS_CANCELLED = 2;

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 检验结果ID (lis_result.id) */
    private Long resultId;

    /** 标本ID */
    private Long sampleId;

    /** 检验指标ID */
    private Long testItemId;

    /** 患者姓名 (冗余) */
    private String patientName;

    /** 检验项目名称 (冗余) */
    private String testItemName;

    /** 结果值 */
    private String resultValue;

    /** 单位 */
    private String unit;

    /** 危急值下限 */
    private String criticalLow;

    /** 危急值上限 */
    private String criticalHigh;

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

    private LocalDateTime updateTime;
}