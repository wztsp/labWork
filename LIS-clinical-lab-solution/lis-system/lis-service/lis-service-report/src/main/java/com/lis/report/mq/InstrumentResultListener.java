package com.lis.report.mq;

import com.lis.api.dto.ResultDTO;
import com.lis.api.service.ResultPersistencePort;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.Exchange;
import org.springframework.amqp.rabbit.annotation.Queue;
import org.springframework.amqp.rabbit.annotation.QueueBinding;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 监听仪器上传结果 (instrument.result.received)，将检验结果落库。
 * <p>
 * 仪器模块 ResultMatchingService 按条码/样本号匹配出 sampleId 后，
 * 通过消息队列发送 List&lt;ResultDTO&gt;。本监听器负责解析并调用 ResultPersistencePort 落库
 * (保存后触发 result.saved 自动生成报告草稿)。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class InstrumentResultListener {

    private final ResultPersistencePort resultPersistencePort;

    @RabbitListener(bindings = @QueueBinding(
            value = @Queue(value = "${mq.report.instrument-result-queue:lis.report.instrument-result}", durable = "true"),
            exchange = @Exchange(value = "${mq.exchange:lis.exchange}", type = "topic"),
            key = "${mq.report.instrument-result-routing-key:instrument.result.received}"
    ))
    public void onInstrumentResults(List<ResultDTO> dtos) {
        if (dtos == null || dtos.isEmpty()) {
            log.warn("收到 instrument.result.received 消息但结果列表为空");
            return;
        }
        log.info("收到仪器结果 {} 条, 开始解析并落库", dtos.size());
        try {
            List<Long> ids = resultPersistencePort.persistResults(dtos);
            log.info("仪器结果落库完成, 保存 {} 条, resultIds={}", ids.size(), ids);
        } catch (Exception e) {
            log.error("仪器结果落库失败", e);
        }
    }
}