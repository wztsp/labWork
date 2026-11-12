package com.lis.hr.job;

import com.lis.hr.mapper.StaffCertificateMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * 证书到期提醒 Job
 * <p>
 * 每日 8 点扫描 expiry_date &gt;= 当前日期 且 expiry_date &lt;= 当前日期+30天 的有效证书,
 * 通过 MQ 发送到 "reminder.certificate" 路由键通知。
 * 消息内容包含: staffId, staffName, certificateName, expiryDate, daysRemaining。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CertificateReminderJob {

    private final StaffCertificateMapper certificateMapper;
    private final RabbitTemplate rabbitTemplate;

    @Value("${mq.exchange:lis.exchange}")
    private String exchange;
    @Value("${mq.reminder.certificate-routing-key:reminder.certificate}")
    private String routingKey;

    /** 提前提醒天数 */
    private static final int ADVANCE_DAYS = 30;

    @Scheduled(cron = "0 0 8 * * ?")
    public void scanCertificateExpiry() {
        LocalDate today = LocalDate.now();
        LocalDate threshold = today.plusDays(ADVANCE_DAYS);
        List<Map<String, Object>> expiring = certificateMapper.listExpiringSoon(threshold);
        if (expiring.isEmpty()) {
            log.info("证书到期提醒: 无到期证书 (区间 {} ~ {})", today, threshold);
            return;
        }
        for (Map<String, Object> cert : expiring) {
            // 统一字段名: certificateName (与任务要求一致), 同时保留 certName 兼容
            Object certName = cert.get("certName");
            cert.put("certificateName", certName);
            // 计算剩余天数 (若 SQL 已返回 daysRemaining 则直接使用)
            Object daysRemaining = cert.get("daysRemaining");
            if (daysRemaining == null) {
                Object expiryObj = cert.get("expiryDate");
                if (expiryObj instanceof java.time.LocalDate) {
                    cert.put("daysRemaining", (int) java.time.temporal.ChronoUnit.DAYS.between(today, (java.time.LocalDate) expiryObj));
                } else {
                    cert.put("daysRemaining", null);
                }
            }
            cert.put("reminderTime", today.toString());
            cert.put("message", "证书到期提醒: " + cert.get("staffName")
                    + " 的 " + certName + " 证书将于 " + cert.get("expiryDate")
                    + " 到期 (剩余 " + cert.get("daysRemaining") + " 天)");
            try {
                rabbitTemplate.convertAndSend(exchange, routingKey, cert);
            } catch (Exception e) {
                log.error("证书到期提醒 MQ 发送失败: certId={}", cert.get("id"), e);
            }
        }
        log.info("证书到期提醒: 扫描到 {} 个到期证书, 已发送 MQ 通知", expiring.size());
    }
}
