package com.lis.integration.job;

import com.lis.integration.zl.HisStandardService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 中联标准服务自动回写任务：
 * S4009 申请状态回写、S3103 危急值回传、S4011 取消报告。
 * 未配置 his.zl.url 时自动跳过。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class HisStandardPushJob {

    private final HisStandardService hisStandardService;

    @Scheduled(cron = "${integration.zl.push-cron:0 */2 * * * ?}")
    public void push() {
        try {
            hisStandardService.pushPendingSampleStatus();
            hisStandardService.pushPendingCritical();
            hisStandardService.pushPendingReportCancel();
        } catch (Exception e) {
            log.error("[标准服务] 自动回写任务执行失败", e);
        }
    }
}
