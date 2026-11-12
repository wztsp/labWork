package com.lis.qc.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.lis.qc.entity.QCRecord;
import com.lis.qc.mapper.QCRecordMapper;
import com.lis.qc.service.TrendAlertService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 质控偏差趋势预警服务实现
 * <p>
 * 每 5 分钟扫描一次最近 20 条质控记录，检测连续偏移趋势：
 * 1. 连续 5 点同侧偏移 → 发送预警
 * 2. 连续 7 点递增/递减 → 发送预警
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TrendAlertServiceImpl implements TrendAlertService {

    private final QCRecordMapper qcRecordMapper;
    private final RabbitTemplate rabbitTemplate;

    @Value("${mq.exchange:lis.exchange}")
    private String exchange;
    @Value("${mq.qc.trend-alert-routing-key:qc.trend.alert}")
    private String trendAlertRoutingKey;

    /** 最近一次告警时间，用于避免对同一段趋势重复告警 */
    private LocalDateTime lastAlertTime = null;

    @Override
    @Scheduled(fixedRate = 300000)
    public void scanTrend() {
        LambdaQueryWrapper<QCRecord> wrapper = new LambdaQueryWrapper<QCRecord>()
                .orderByDesc(QCRecord::getMeasureTime)
                .last("LIMIT 20");
        List<QCRecord> records = qcRecordMapper.selectList(wrapper);
        if (records.size() < 7) {
            return;
        }
        // 反转为升序：从早到晚
        java.util.Collections.reverse(records);

        boolean alert5SameSide = checkSameSideBias(records, 5);
        boolean alert7Trend = checkTrend(records, 7);

        if (alert5SameSide || alert7Trend) {
            sendAlert(records, alert5SameSide, alert7Trend);
        }
    }

    /**
     * 连续 N 点同侧偏移 (Z 分数符号相同)
     */
    private boolean checkSameSideBias(List<QCRecord> records, int n) {
        int size = records.size();
        if (size < n) return false;
        // 取最后 n 个点
        int start = size - n;
        int positive = 0;
        int negative = 0;
        for (int i = start; i < size; i++) {
            BigDecimal z = records.get(i).getZScore();
            if (z == null) return false;
            if (z.compareTo(BigDecimal.ZERO) > 0) positive++;
            else if (z.compareTo(BigDecimal.ZERO) < 0) negative++;
        }
        return positive == n || negative == n;
    }

    /**
     * 连续 N 点递增或递减
     */
    private boolean checkTrend(List<QCRecord> records, int n) {
        int size = records.size();
        if (size < n) return false;
        int start = size - n;
        boolean increasing = true;
        boolean decreasing = true;
        for (int i = start + 1; i < size; i++) {
            BigDecimal prev = records.get(i - 1).getMeasureValue();
            BigDecimal cur = records.get(i).getMeasureValue();
            if (prev == null || cur == null) return false;
            int cmp = cur.compareTo(prev);
            if (cmp <= 0) increasing = false;
            if (cmp >= 0) decreasing = false;
        }
        return increasing || decreasing;
    }

    private void sendAlert(List<QCRecord> records, boolean sameSide, boolean trend) {
        LocalDateTime now = LocalDateTime.now();
        // 同一窗口内避免重复告警 (1 分钟内)
        if (lastAlertTime != null && lastAlertTime.plusMinutes(1).isAfter(now)) {
            return;
        }
        lastAlertTime = now;

        QCRecord last = records.get(records.size() - 1);
        Map<String, Object> payload = new HashMap<>();
        payload.put("alertType", sameSide && trend ? "TREND_AND_SAME_SIDE" : (sameSide ? "SAME_SIDE_BIAS" : "TREND"));
        payload.put("lotId", last.getQcLotId());
        payload.put("testItemId", last.getTestItemId());
        payload.put("instrumentId", last.getInstrumentId());
        payload.put("lastMeasureValue", last.getMeasureValue());
        payload.put("lastZScore", last.getZScore());
        payload.put("alertTime", now.toString());
        payload.put("message", sameSide
                ? "连续 5 点同侧偏移"
                : "连续 7 点单调趋势");

        try {
            rabbitTemplate.convertAndSend(exchange, trendAlertRoutingKey, payload);
            log.warn("质控趋势预警已发送: {}", payload);
        } catch (Exception e) {
            log.error("质控趋势预警发送失败", e);
        }
    }
}
