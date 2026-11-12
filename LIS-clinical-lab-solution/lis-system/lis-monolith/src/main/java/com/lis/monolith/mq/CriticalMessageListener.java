package com.lis.monolith.mq;

import com.lis.monolith.entity.Message;
import com.lis.monolith.mapper.TatDataMapper;
import com.lis.monolith.service.MessageService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.Exchange;
import org.springframework.amqp.rabbit.annotation.Queue;
import org.springframework.amqp.rabbit.annotation.QueueBinding;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * 危急值消息生成监听器
 * 监听危急值告警事件, 生成站内消息提醒 (独立队列, 与外部通知不冲突)
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CriticalMessageListener {

    private final MessageService messageService;
    private final TatDataMapper tatDataMapper;

    @RabbitListener(bindings = @QueueBinding(
            value = @Queue(value = "${mq.message.critical-queue:critical.message}", durable = "true"),
            exchange = @Exchange(value = "${mq.exchange:lis.exchange}", type = "topic"),
            key = "${mq.critical.alert-routing-key:critical.alert}"
    ))
    public void onCritical(Map<String, Object> payload) {
        try {
            Object resultIdObj = payload.get("resultId");
            if (resultIdObj == null) {
                return;
            }
            Long resultId = Long.valueOf(String.valueOf(resultIdObj));
            Map<String, Object> detail = tatDataMapper.selectCriticalDetail(resultId);
            String patient = detail != null && detail.get("patientName") != null ? String.valueOf(detail.get("patientName")) : "";
            String itemName = detail != null && detail.get("testItemName") != null ? String.valueOf(detail.get("testItemName")) : "";
            String value = payload.get("resultValue") != null ? String.valueOf(payload.get("resultValue")) : "";
            String unit = payload.get("unit") != null ? String.valueOf(payload.get("unit")) : "";
            Long sampleId = detail != null && detail.get("sampleId") != null ? Long.valueOf(String.valueOf(detail.get("sampleId"))) : null;
            Long testItemId = detail != null && detail.get("testItemId") != null ? Long.valueOf(String.valueOf(detail.get("testItemId"))) : null;

            String title = "危急值告警";
            String content = String.format("患者 %s 检验项目 %s 结果 %s%s 为危急值, 请立即处理!",
                    patient, itemName, value, unit);
            // 危急值面向全员可见
            messageService.broadcast(Message.TYPE_CRITICAL, title, content, resultId, sampleId, testItemId);
            log.warn("危急值站内消息已生成: resultId={}", resultId);
        } catch (Exception e) {
            log.error("危急值站内消息生成失败", e);
        }
    }
}