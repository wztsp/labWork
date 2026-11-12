package com.lis.qc.service;

import java.time.LocalDate;
import java.util.Map;

/**
 * 质控图数据服务
 */
public interface QCChartService {

    /**
     * Levey-Jennings 图数据
     * 返回包含: targetValue, sd, points[{measureValue, zScore, measureTime, qcResult}]
     *
     * @param lotId      质控品批号ID
     * @param limit      返回点数上限 (<=0 时使用默认)
     * @param beginDate  开始日期 (含, 可空)
     * @param endDate    结束日期 (含, 可空)
     */
    Map<String, Object> getLeveyJenningsData(Long lotId, int limit, LocalDate beginDate, LocalDate endDate);

    /**
     * Z-分数图数据
     * 返回包含: points[{zScore, measureTime, qcResult, ruleViolation}]
     *
     * @param lotId      质控品批号ID
     * @param limit      返回点数上限 (<=0 时使用默认)
     * @param beginDate  开始日期 (含, 可空)
     * @param endDate    结束日期 (含, 可空)
     */
    Map<String, Object> getZScoreData(Long lotId, int limit, LocalDate beginDate, LocalDate endDate);
}
