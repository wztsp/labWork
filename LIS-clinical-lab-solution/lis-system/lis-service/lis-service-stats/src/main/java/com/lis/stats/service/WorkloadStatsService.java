package com.lis.stats.service;

import java.util.List;
import java.util.Map;

/**
 * 工作量统计服务
 */
public interface WorkloadStatsService {

    /**
     * 工作量汇总: 总标本量、今日标本量、急诊标本量、已报告数
     */
    Map<String, Object> getWorkloadSummary();

    /**
     * 按日统计标本量 (柱状图数据)
     */
    List<Map<String, Object>> getWorkloadByDay(String startDate, String endDate);

    /**
     * 按科室统计标本量 (饼图数据)
     */
    List<Map<String, Object>> getWorkloadByDept(String startDate, String endDate);

    /**
     * 按仪器统计标本量
     */
    List<Map<String, Object>> getWorkloadByInstrument(String startDate, String endDate);
}
