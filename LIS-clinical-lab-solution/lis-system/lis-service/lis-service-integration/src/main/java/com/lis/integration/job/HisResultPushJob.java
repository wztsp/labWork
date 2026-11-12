package com.lis.integration.job;

import com.lis.integration.service.HisResultPushService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 检验结果回传 HIS 定时任务(S4010)。
 * 默认每 2 分钟扫描一次已发布未同步的报告；未配置 his.result.url 时自动跳过。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class HisResultPushJob {

    private final HisResultPushService hisResultPushService;

    @Scheduled(cron = "${integration.result.push-cron:0 */2 * * * ?}")
    public void pushResults() {
        try {
            hisResultPushService.pushPending();
        } catch (Exception e) {
            log.error("[HIS结果回传] 定时任务执行失败", e);
        }
    }
}
