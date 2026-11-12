package com.lis.stats.service;

import java.util.List;
import java.util.Map;

/**
 * 趋势分析服务
 */
public interface TrendAnalysisService {

    /**
     * 患者历史结果趋势 (按 patientId + testItemId)
     */
    List<Map<String, Object>> getPatientTrend(Long patientId, Long testItemId, int limit);

    /**
     * 阳性率统计 (按项目/时间维度, 可选标本类型过滤)
     */
    List<Map<String, Object>> getPositiveRate(String startDate, String endDate, Long testItemId);

    /**
     * 阳性率统计 (按项目/时间/标本类型维度)
     */
    List<Map<String, Object>> getPositiveRate(String startDate, String endDate, Long testItemId, String specimenType);

    /**
     * 阳性率趋势 (按日期分组, 可选标本类型过滤), 用于折线图
     */
    List<Map<String, Object>> getPositiveRateTrend(String startDate, String endDate, Long testItemId, String specimenType);
}
