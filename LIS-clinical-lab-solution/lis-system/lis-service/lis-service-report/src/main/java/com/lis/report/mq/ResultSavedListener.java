package com.lis.report.mq;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.lis.report.entity.Result;
import com.lis.report.mapper.ResultMapper;
import com.lis.report.service.ReportService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.Exchange;
import org.springframework.amqp.rabbit.annotation.Queue;
import org.springframework.amqp.rabbit.annotation.QueueBinding;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 监听仪器结果保存事件 (result.saved)，自动生成报告草稿
 * <p>
 * 当仪器结果保存后，自动检查该标本所有申请项目是否都有结果，
 * 如果全部出齐则调用 reportService.generateReport(sampleId) 生成报告草稿。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ResultSavedListener {

    private final ResultMapper resultMapper;
    private final ReportService reportService;

    @RabbitListener(bindings = @QueueBinding(
            value = @Queue(value = "${mq.report.result-saved-queue:lis.report.result-saved}", durable = "true"),
            exchange = @Exchange(value = "${mq.exchange:lis.exchange}", type = "topic"),
            key = "${mq.report.result-saved-routing-key:result.saved}"
    ))
    public void onResultSaved(Map<String, Object> payload) {
        Object sampleIdObj = payload == null ? null : payload.get("sampleId");
        if (sampleIdObj == null) {
            log.warn("收到 result.saved 消息但缺少 sampleId: {}", payload);
            return;
        }
        Long sampleId = ((Number) sampleIdObj).longValue();
        log.info("收到 result.saved 消息: sampleId={}", sampleId);

        try {
            // 1. 查询该标本预期的检验项目 ID 列表
            List<Long> expectedItemIds = resultMapper.selectExpectedItemIdsBySample(sampleId);
            if (expectedItemIds == null || expectedItemIds.isEmpty()) {
                log.debug("标本 {} 无预期项目配置, 跳过自动生成报告", sampleId);
                return;
            }

            // 2. 查询该标本已保存的结果项目 ID 集合
            List<Result> results = resultMapper.selectList(
                    new LambdaQueryWrapper<Result>().eq(Result::getSampleId, sampleId));
            if (results.isEmpty()) {
                log.debug("标本 {} 暂无结果, 跳过自动生成报告", sampleId);
                return;
            }
            Set<Long> actualItemIds = results.stream()
                    .map(Result::getTestItemId)
                    .filter(id -> id != null)
                    .collect(Collectors.toSet());

            // 3. 检查所有预期项目是否都有结果
            Set<Long> expectedSet = new HashSet<>(expectedItemIds);
            if (!actualItemIds.containsAll(expectedSet)) {
                expectedSet.removeAll(actualItemIds);
                log.info("标本 {} 项目未出齐, 缺少 {} 项, 跳过自动生成报告", sampleId, expectedSet.size());
                return;
            }

            // 4. 全部出齐，生成报告草稿
            reportService.generateReport(sampleId);
            log.info("标本 {} 所有项目结果已出齐, 已自动生成报告草稿", sampleId);
        } catch (Exception e) {
            log.error("自动生成报告失败: sampleId={}", sampleId, e);
        }
    }
}
