package com.lis.qc.service;

/**
 * 质控偏差趋势预警服务
 */
public interface TrendAlertService {

    /**
     * 扫描最近的质控记录，检测连续偏移趋势
     */
    void scanTrend();
}
