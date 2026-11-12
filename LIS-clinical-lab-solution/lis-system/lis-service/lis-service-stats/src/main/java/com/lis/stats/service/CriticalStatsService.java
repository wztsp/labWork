package com.lis.stats.service;

import java.util.List;
import java.util.Map;

/**
 * 危急值统计服务
 */
public interface CriticalStatsService {

    /**
     * 危急值汇总: 总数、已确认数、确认率、平均确认时长(秒)。
     * 可按日期范围过滤 (基于结果检测时间)。
     */
    Map<String, Object> getCriticalSummary(String startDate, String endDate);

    /**
     * 按项目统计危急值
     */
    List<Map<String, Object>> getCriticalByItem(String startDate, String endDate);

    /**
     * 按科室统计危急值
     */
    List<Map<String, Object>> getCriticalByDept(String startDate, String endDate);

    /**
     * 按日统计危急值 (用于折线/柱状图)
     */
    List<Map<String, Object>> getCriticalByDay(String startDate, String endDate);
}
