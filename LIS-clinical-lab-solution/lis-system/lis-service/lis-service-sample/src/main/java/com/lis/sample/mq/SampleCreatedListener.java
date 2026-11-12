package com.lis.sample.mq;

import com.lis.sample.entity.Sample;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.Queue;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

/**
 * 标本创建消息监听器
 * 监听 sample.created 队列，异步处理
 */
@Slf4j
@Component
public class SampleCreatedListener {

    @RabbitListener(queuesToDeclare = @Queue("sample.created"))
    public void onCreated(Sample sample) {
        log.info("收到标本创建消息, 异步处理开始. barcode={}, sampleId={}",
                sample.getSampleBarcode(), sample.getId());
        // 异步处理(打印日志)
        log.info("标本 {} 已创建, 通知采血台打印条码标签", sample.getSampleBarcode());
        log.info("标本 {} 异步处理完成", sample.getSampleBarcode());
    }
}
