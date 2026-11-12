package com.lis.monolith.task;

import com.lis.monolith.service.TatMonitorService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * TAT 超时监控定时任务
 * 每 5 分钟扫描一次未报告标本, 判断是否超时并生成提醒
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TatMonitorTask {

    private final TatMonitorService tatMonitorService;

    @Scheduled(fixedDelayString = "${tat.scan.interval-ms:300000}", initialDelay = 30000)
    public void scan() {
        try {
            tatMonitorService.runScan();
        } catch (Exception e) {
            log.error("TAT超时扫描失败", e);
        }
    }
}