package com.lis.report.service;

import com.lis.report.dto.CriticalRecordDTO;
import com.lis.report.vo.CriticalRecordVO;

import java.util.List;
import java.util.Map;

/**
 * 危急值服务
 */
public interface CriticalValueService {

    /**
     * 检查结果是否危急值
     */
    boolean checkCritical(Long resultId);

    /**
     * 危急值通知 (发送 MQ 消息 critical.alert)
     */
    boolean alertCritical(Long resultId);

    /**
     * 获取危急值列表 (含处理记录: 通知人/通知时间/发送人/发送时间/备注/状态)
     *
     * @param startDate 起始日期 (yyyy-MM-dd, 可空=不限)
     * @param endDate   截止日期 (yyyy-MM-dd, 可空=不限)
     */
    List<CriticalRecordVO> getCriticalList(String startDate, String endDate);

    /**
     * 确认危急值
     */
    boolean confirmCritical(Long resultId, Long confirmerId);

    /**
     * 危急值通知: 保存/更新处理记录 (通知人/通知时间/发送人/发送时间/备注), 状态置为已通知(1)
     * 同时发送 MQ 危急值消息
     */
    boolean notifyCritical(Long resultId, CriticalRecordDTO dto);

    /**
     * 取消通知: 状态置为已取消(2)
     */
    boolean cancelNotify(Long resultId);

    /**
     * 修改危急值处理记录 (通知人/通知时间/发送人/发送时间/备注, 可修改时间)
     */
    boolean updateRecord(Long resultId, CriticalRecordDTO dto);

    /**
     * 统计指定时间范围内的危急值数量、确认率、平均确认时长
     *
     * @param startDate 起始日期 (yyyy-MM-dd)
     * @param endDate   截止日期 (yyyy-MM-dd)
     */
    Map<String, Object> getStats(String startDate, String endDate);
}