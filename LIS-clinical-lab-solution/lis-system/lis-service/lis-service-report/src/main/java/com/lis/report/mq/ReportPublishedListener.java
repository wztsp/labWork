package com.lis.report.mq;

import com.lis.api.vo.ReportVO;
import com.lis.report.entity.Report;
import com.lis.report.mapper.ReportMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.Exchange;
import org.springframework.amqp.rabbit.annotation.Queue;
import org.springframework.amqp.rabbit.annotation.QueueBinding;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

/**
 * 监听报告发布事件，异步回传 HIS
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ReportPublishedListener {

    private final ReportMapper reportMapper;

    @RabbitListener(bindings = @QueueBinding(
            value = @Queue(value = "${mq.report.published-queue:report.published}", durable = "true"),
            exchange = @Exchange(value = "${mq.exchange:lis.exchange}", type = "topic"),
            key = "${mq.report.published-routing-key:report.published}"
    ))
    public void onReportPublished(ReportVO vo) {
        log.info("收到报告发布消息: reportId={}, reportNo={}", vo.getId(), vo.getReportNo());
        try {
            // 简化: 调用 integration 服务回传 HIS (实际通过 Feign 或 RabbitMQ)
            // 此处仅更新 HIS 同步状态
            Report report = reportMapper.selectById(vo.getId());
            if (report != null) {
                report.setHisSyncStatus(1); // 1=已同步 (简化)
                report.setHisSyncTime(LocalDateTime.now());
                report.setUpdateTime(LocalDateTime.now());
                reportMapper.updateById(report);
                log.info("报告 HIS 同步标记完成: reportId={}", vo.getId());
            }
        } catch (Exception e) {
            log.error("HIS 回传失败: reportId={}", vo.getId(), e);
            Report report = reportMapper.selectById(vo.getId());
            if (report != null) {
                report.setHisSyncStatus(2); // 2=同步失败
                report.setUpdateTime(LocalDateTime.now());
                reportMapper.updateById(report);
            }
        }
    }
}
