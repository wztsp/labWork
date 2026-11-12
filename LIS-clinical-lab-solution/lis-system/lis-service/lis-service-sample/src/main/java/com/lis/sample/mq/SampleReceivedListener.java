package com.lis.sample.mq;

import com.lis.sample.entity.Sample;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.Queue;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

/**
 * 标本核收消息监听器
 * 监听 sample.received 队列，异步分配仪器
 */
@Slf4j
@Component
public class SampleReceivedListener {

    @RabbitListener(queuesToDeclare = @Queue("sample.received"))
    public void onReceived(Sample sample) {
        log.info("收到标本核收消息, 开始异步分配仪器. barcode={}, sampleId={}",
                sample.getSampleBarcode(), sample.getId());
        // 异步分配仪器(打印日志表示分配)
        log.info("标本 {} 已核收，分配仪器: 自动分配可用仪器[INSTR-001]", sample.getSampleBarcode());
        log.info("标本 {} 仪器分配完成, 待检测", sample.getSampleBarcode());
    }
}
