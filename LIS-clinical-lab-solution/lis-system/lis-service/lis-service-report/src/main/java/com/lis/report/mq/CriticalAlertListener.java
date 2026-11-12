package com.lis.report.mq;

import com.lis.common.notify.NotifyService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.Exchange;
import org.springframework.amqp.rabbit.annotation.Queue;
import org.springframework.amqp.rabbit.annotation.QueueBinding;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * 监听危急值告警事件，异步发送通知 (短信/电话/IM)
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CriticalAlertListener {

    private final List<NotifyService> notifyServices;

    @RabbitListener(bindings = @QueueBinding(
            value = @Queue(value = "${mq.critical.alert-queue:critical.alert}", durable = "true"),
            exchange = @Exchange(value = "${mq.exchange:lis.exchange}", type = "topic"),
            key = "${mq.critical.alert-routing-key:critical.alert}"
    ))
    public void onCriticalAlert(Map<String, Object> payload) {
        log.warn("收到危急值告警消息: {}", payload);
        Object resultId = payload.get("resultId");
        Object value = payload.get("resultValue");
        log.warn("【危急值通知】 resultId={}, value={}, 请相关医师立即确认!", resultId, value);

        // 通过所有 Notifier 发送通知
        String title = "危急值告警";
        String content = buildContent(payload);
        for (NotifyService notifier : notifyServices) {
            try {
                notifier.sendAlert(title, content, List.of());
            } catch (Exception e) {
                log.error("通知发送失败: notifier={}", notifier.getClass().getSimpleName(), e);
            }
        }
    }

    private String buildContent(Map<String, Object> payload) {
        StringBuilder sb = new StringBuilder();
        sb.append("结果ID: ").append(payload.get("resultId")).append("\n");
        sb.append("标本ID: ").append(payload.get("sampleId")).append("\n");
        sb.append("检验项目ID: ").append(payload.get("testItemId")).append("\n");
        sb.append("结果值: ").append(payload.get("resultValue")).append("\n");
        sb.append("单位: ").append(payload.get("unit")).append("\n");
        sb.append("告警时间: ").append(payload.get("alertTime"));
        return sb.toString();
    }
}
