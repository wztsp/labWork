package com.lis.instrument.mq;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.Exchange;
import org.springframework.amqp.rabbit.annotation.Queue;
import org.springframework.amqp.rabbit.annotation.QueueBinding;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * 仪器离线告警监听器
 * <p>
 * 监听 instrument.offline.alert 队列，处理仪器离线告警
 * (实际场景: 发送短信/邮件/IM 通知运维人员)
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class InstrumentAlertListener {

    @RabbitListener(bindings = @QueueBinding(
            value = @Queue(value = "${mq.instrument.offline-alert-queue:instrument.offline.alert}", durable = "true"),
            exchange = @Exchange(value = "${mq.exchange:lis.exchange}", type = "topic"),
            key = "${mq.instrument.offline-alert-routing-key:instrument.offline.alert}"
    ))
    public void onOfflineAlert(Map<String, Object> payload) {
        log.warn("收到仪器离线告警: {}", payload);
        Object instrumentId = payload.get("instrumentId");
        Object instrumentCode = payload.get("instrumentCode");
        Object message = payload.get("message");
        // 简化: 实际场景应推送至短信/电话/IM 系统通知运维人员
        log.warn("【仪器离线告警】 instrumentId={}, code={}, message={}, 请运维人员立即处理!",
                instrumentId, instrumentCode, message);
    }
}
