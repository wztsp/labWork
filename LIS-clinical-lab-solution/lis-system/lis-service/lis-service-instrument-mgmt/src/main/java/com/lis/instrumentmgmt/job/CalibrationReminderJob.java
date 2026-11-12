package com.lis.instrumentmgmt.job;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.lis.instrumentmgmt.entity.InstrumentMaintenancePlan;
import com.lis.instrumentmgmt.mapper.InstrumentMaintenancePlanMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 校准到期提醒 Job
 * <p>
 * 每日 8 点扫描校准计划 (plan_type=CALIBRATION) 中 next_date &lt;= 当前日期+7天 的记录,
 * 通过 MQ 发送到 "reminder.calibration" 路由键通知。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CalibrationReminderJob {

    private final InstrumentMaintenancePlanMapper planMapper;
    private final RabbitTemplate rabbitTemplate;

    @Value("${mq.exchange:lis.exchange}")
    private String exchange;
    @Value("${mq.reminder.calibration-routing-key:reminder.calibration}")
    private String routingKey;

    /** 提前提醒天数 */
    private static final int ADVANCE_DAYS = 7;

    @Scheduled(cron = "0 0 8 * * ?")
    public void scanCalibrationDue() {
        LocalDate threshold = LocalDate.now().plusDays(ADVANCE_DAYS);
        List<InstrumentMaintenancePlan> duePlans = planMapper.selectList(
                new LambdaQueryWrapper<InstrumentMaintenancePlan>()
                        .eq(InstrumentMaintenancePlan::getPlanType, "CALIBRATION")
                        .eq(InstrumentMaintenancePlan::getStatus, 1)
                        .le(InstrumentMaintenancePlan::getNextDate, threshold));
        if (duePlans.isEmpty()) {
            log.info("校准到期提醒: 无到期校准计划 (截至 {})", threshold);
            return;
        }
        for (InstrumentMaintenancePlan plan : duePlans) {
            Map<String, Object> payload = new HashMap<>();
            payload.put("planId", plan.getId());
            payload.put("instrumentId", plan.getInstrumentId());
            payload.put("planType", plan.getPlanType());
            payload.put("cycleType", plan.getCycleType());
            payload.put("nextDate", plan.getNextDate() == null ? null : plan.getNextDate().toString());
            payload.put("reminderTime", LocalDate.now().toString());
            payload.put("message", "校准计划到期提醒: 仪器 " + plan.getInstrumentId()
                    + " 校准日期 " + plan.getNextDate());
            try {
                rabbitTemplate.convertAndSend(exchange, routingKey, payload);
            } catch (Exception e) {
                log.error("校准到期提醒 MQ 发送失败: planId={}", plan.getId(), e);
            }
        }
        log.info("校准到期提醒: 扫描到 {} 个到期校准计划, 已发送 MQ 通知", duePlans.size());
    }
}
