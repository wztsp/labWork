package com.lis.reagent.job;

import com.lis.reagent.service.ReagentStockService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 试剂定时任务 - 效期预警与过期批次锁定
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ExpiryAlertJob {

    private final ReagentStockService stockService;

    @Value("${reagent.expiry.near-expiry-days:30}")
    private int nearExpiryDays;

    /**
     * 每天凌晨 1 点执行: 锁定过期批次 + 打印近效期预警
     */
    @Scheduled(cron = "0 0 1 * * ?")
    public void executeDaily() {
        log.info("[ExpiryAlertJob] 开始执行效期检查...");
        int locked = stockService.lockExpiredStock();
        log.info("[ExpiryAlertJob] 锁定过期批次: {} 个", locked);

        int nearExpiryCount = stockService.listNearExpiry(nearExpiryDays).size();
        log.info("[ExpiryAlertJob] 近效期({}天内)批次: {} 个", nearExpiryDays, nearExpiryCount);

        int belowSafety = stockService.listBelowSafetyStock().size();
        log.info("[ExpiryAlertJob] 安全库存预警试剂: {} 个", belowSafety);
    }
}
