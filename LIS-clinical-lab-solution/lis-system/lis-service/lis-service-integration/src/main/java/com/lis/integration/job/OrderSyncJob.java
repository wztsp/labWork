package com.lis.integration.job;

import com.lis.api.dto.OrderDTO;
import com.lis.integration.adapter.HisAdapter;
import com.lis.integration.adapter.HisAdapterFactory;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.Date;
import java.util.List;

/**
 * 申请同步定时任务
 * <p>
 * 简化: 每 5 分钟从 HIS 拉取检验申请，写入标本服务。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OrderSyncJob {

    private final HisAdapterFactory adapterFactory;

    @Value("${integration.his.default-vendor:UNIVERSAL_REST}")
    private String defaultVendor;

    /**
     * 每 5 分钟拉取一次
     */
    @Scheduled(cron = "${integration.sync.order-cron:0 */5 * * * ?}")
    public void syncOrders() {
        log.info("[OrderSyncJob] 开始同步 HIS 检验申请...");
        try {
            HisAdapter adapter = adapterFactory.getAdapter(defaultVendor);
            long endTs = System.currentTimeMillis();
            long startTs = endTs - 5 * 60 * 1000;
            List<OrderDTO> orders = adapter.receiveOrders(new Date(startTs), new Date(endTs));
            if (orders == null || orders.isEmpty()) {
                log.info("[OrderSyncJob] 本次无新增申请");
                return;
            }
            // 简化: 实际场景应通过 Feign 调用标本服务创建标本
            for (OrderDTO order : orders) {
                log.info("[OrderSyncJob] 收到申请: orderNo={}, patient={}",
                        order.getOrderNo(), order.getPatientName());
            }
            log.info("[OrderSyncJob] 同步完成: 共 {} 条申请", orders.size());
        } catch (Exception e) {
            log.error("[OrderSyncJob] 同步异常", e);
        }
    }
}
