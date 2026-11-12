package com.lis.monolith.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.lis.monolith.entity.TatMonitor;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * TAT 业务监控服务
 */
public interface TatMonitorService {

    /** 手动/定时扫描标本, 计算 TAT 是否超时, 生成监控记录与超时消息, 返回新增处理条数 */
    int runScan();

    IPage<TatMonitor> page(int page, int size, Integer isOverdue, String keyword,
                           LocalDateTime startTime, LocalDateTime endTime);

    TatMonitor getById(Long id);

    /** 汇总统计 (可按时间范围过滤) */
    Map<String, Object> stats(LocalDateTime startTime, LocalDateTime endTime);
}