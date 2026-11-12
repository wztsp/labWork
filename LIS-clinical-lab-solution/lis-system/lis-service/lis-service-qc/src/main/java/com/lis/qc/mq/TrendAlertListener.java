package com.lis.qc.mq;

import com.lis.qc.service.QcAlertService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.Exchange;
import org.springframework.amqp.rabbit.annotation.Queue;
import org.springframework.amqp.rabbit.annotation.QueueBinding;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * 监听质控偏差趋势预警消息 (路由键 qc.trend.alert), 将预警信息持久化到数据库。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TrendAlertListener {

    private final QcAlertService qcAlertService;

    @RabbitListener(bindings = @QueueBinding(
            value = @Queue(value = "${mq.qc.trend-alert-queue:qc.trend.alert}", durable = "true"),
            exchange = @Exchange(value = "${mq.exchange:lis.exchange}", type = "topic"),
            key = "${mq.qc.trend-alert-routing-key:qc.trend.alert}"
    ))
    public void onTrendAlert(Map<String, Object> payload) {
        log.info("收到质控趋势预警消息: {}", payload);
        try {
            qcAlertService.saveAlert(payload);
        } catch (Exception e) {
            log.error("质控趋势预警入库失败: {}", payload, e);
        }
    }
}
