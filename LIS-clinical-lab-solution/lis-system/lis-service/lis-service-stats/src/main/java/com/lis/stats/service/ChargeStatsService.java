package com.lis.stats.service;

import java.util.List;
import java.util.Map;

/**
 * 收费统计服务
 */
public interface ChargeStatsService {

    /**
     * 费用汇总: 总费用、今日费用、项目明细数、申请单数。可按日期范围与科室过滤。
     */
    Map<String, Object> getChargeSummary(String startDate, String endDate, String department);

    /**
     * 按日统计费用 (用于折线/柱状图)。可按科室过滤。
     */
    List<Map<String, Object>> getChargeByDay(String startDate, String endDate, String department);

    /**
     * 按科室统计费用
     */
    List<Map<String, Object>> getChargeByDept(String startDate, String endDate);

    /**
     * 按项目统计费用。可按科室过滤。
     */
    List<Map<String, Object>> getChargeByItem(String startDate, String endDate, String department);
}
