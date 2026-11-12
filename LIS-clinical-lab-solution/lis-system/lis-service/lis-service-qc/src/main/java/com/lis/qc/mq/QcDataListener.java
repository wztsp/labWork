package com.lis.qc.mq;

import com.lis.api.dto.QCRecordDTO;
import com.lis.qc.service.QCRecordService;
import com.lis.qc.service.rule.QCResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.Exchange;
import org.springframework.amqp.rabbit.annotation.Queue;
import org.springframework.amqp.rabbit.annotation.QueueBinding;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;
import java.util.Map;

/**
 * 监听仪器推送的质控数据 (路由键 qc.data), 自动保存并触发 Westgard 判定。
 * <p>
 * 消息体字段: lotId, instrumentId, value, testTime(可选, ISO 格式), operatorId(可选)
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class QcDataListener {

    private final QCRecordService qcRecordService;

    @RabbitListener(bindings = @QueueBinding(
            value = @Queue(value = "${mq.qc.data-queue:qc.data}", durable = "true"),
            exchange = @Exchange(value = "${mq.exchange:lis.exchange}", type = "topic"),
            key = "${mq.qc.data-routing-key:qc.data}"
    ))
    public void onQcData(Map<String, Object> payload) {
        log.info("收到质控数据消息: {}", payload);
        try {
            Object lotIdObj = payload.get("lotId");
            Object valueObj = payload.get("value");
            if (lotIdObj == null || valueObj == null) {
                log.warn("质控数据消息缺少 lotId 或 value, 忽略: {}", payload);
                return;
            }
            Long lotId = toLong(lotIdObj);
            BigDecimal value = toBigDecimal(valueObj);
            if (lotId == null || value == null) {
                log.warn("质控数据消息 lotId/value 格式非法, 忽略: {}", payload);
                return;
            }
            QCRecordDTO dto = QCRecordDTO.builder()
                    .qcLotId(lotId)
                    .instrumentId(toLong(payload.get("instrumentId")))
                    .measureValue(value)
                    .measureTime(parseTime(payload.get("testTime")))
                    .operatorId(toLong(payload.get("operatorId")))
                    .build();
            QCResult result = qcRecordService.submitRecord(dto);
            log.info("MQ 质控数据入库完成: lotId={}, value={}, result={}", lotId, value, result.getLevel());
        } catch (Exception e) {
            log.error("处理质控数据消息失败: {}", payload, e);
        }
    }

    private Long toLong(Object obj) {
        if (obj == null) return null;
        if (obj instanceof Number n) return n.longValue();
        try {
            return Long.valueOf(obj.toString());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private BigDecimal toBigDecimal(Object obj) {
        if (obj == null) return null;
        if (obj instanceof BigDecimal bd) return bd;
        if (obj instanceof Number n) return BigDecimal.valueOf(n.doubleValue());
        try {
            return new BigDecimal(obj.toString());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private LocalDateTime parseTime(Object obj) {
        if (obj == null) return null;
        String s = obj.toString();
        try {
            return LocalDateTime.parse(s);
        } catch (DateTimeParseException e) {
            log.warn("无法解析时间: {}", s);
            return null;
        }
    }
}
