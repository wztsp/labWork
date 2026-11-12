package com.lis.qc.service;

import java.util.Map;

/**
 * 质控月报服务
 */
public interface QcMonthlyReportService {

    /**
     * 生成月报
     * @param year  年
     * @param month 月 (1-12)
     * @return 报告数据
     */
    Map<String, Object> generateMonthlyReport(int year, int month);
}
