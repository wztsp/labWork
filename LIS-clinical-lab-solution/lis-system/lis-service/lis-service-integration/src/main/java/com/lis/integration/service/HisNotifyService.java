package com.lis.integration.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lis.integration.adapter.HisAdapter;
import com.lis.integration.adapter.HisAdapterFactory;
import com.lis.integration.dto.CriticalValueAlert;
import com.lis.integration.dto.SampleStatusUpdate;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import javax.sql.DataSource;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * HIS 通知服务
 * <p>
 * 通过 RabbitMQ 监听危急值与标本状态变更事件, 并回传到 HIS。
 * <ul>
 *     <li>监听 "critical.alert" 路由键: 将危急值信息 POST 到 HIS</li>
 *     <li>监听 "sample.status.changed" 路由键: 将标本状态变更 POST 到 HIS</li>
 * </ul>
 * 如果 HIS 接口不可用, 记录到 {@code lis_his_sync_retry} 重试表, 定时重试。
 */
@Slf4j
@Service
public class HisNotifyService {

    private final HisAdapterFactory adapterFactory;
    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Value("${integration.his.default-vendor:UNIVERSAL_REST}")
    private String defaultVendor;

    /** 单次重试最大条数 */
    private static final int RETRY_BATCH_SIZE = 100;
    /** 默认最大重试次数 */
    private static final int DEFAULT_MAX_RETRY = 6;

    @Autowired
    public HisNotifyService(HisAdapterFactory adapterFactory, DataSource dataSource) {
        this.adapterFactory = adapterFactory;
        this.jdbcTemplate = new JdbcTemplate(dataSource);
    }

    /**
     * 监听危急值告警
     */
    @RabbitListener(bindings = @org.springframework.amqp.rabbit.annotation.QueueBinding(
            value = @org.springframework.amqp.rabbit.annotation.Queue(value = "q.lis.critical.alert", durable = "false"),
            exchange = @org.springframework.amqp.rabbit.annotation.Exchange(value = "topic.lis", type = "topic"),
            key = "critical.alert"))
    public void onCriticalAlert(CriticalValueAlert alert) {
        log.info("[HisNotify] 收到危急值告警: patient={}, item={}",
                alert.getPatientName(), alert.getItemCode());
        try {
            HisAdapter adapter = adapterFactory.getAdapter(defaultVendor);
            boolean ok = adapter.sendCriticalValueAlert(alert);
            if (!ok) {
                saveRetry("critical", toJson(alert), "HIS 返回失败");
            }
        } catch (Exception e) {
            log.error("[HisNotify] 回传危急值失败, 记录到重试表", e);
            saveRetry("critical", toJson(alert), e.getMessage());
        }
    }

    /**
     * 监听标本状态变更
     */
    @RabbitListener(bindings = @org.springframework.amqp.rabbit.annotation.QueueBinding(
            value = @org.springframework.amqp.rabbit.annotation.Queue(value = "q.lis.sample.status", durable = "false"),
            exchange = @org.springframework.amqp.rabbit.annotation.Exchange(value = "topic.lis", type = "topic"),
            key = "sample.status.changed"))
    public void onSampleStatusChanged(SampleStatusUpdate update) {
        log.info("[HisNotify] 收到标本状态变更: orderNo={}, status={}",
                update.getOrderNo(), update.getStatus());
        try {
            HisAdapter adapter = adapterFactory.getAdapter(defaultVendor);
            boolean ok = adapter.sendSampleStatus(update);
            if (!ok) {
                saveRetry("sample-status", toJson(update), "HIS 返回失败");
            }
        } catch (Exception e) {
            log.error("[HisNotify] 回传标本状态失败, 记录到重试表", e);
            saveRetry("sample-status", toJson(update), e.getMessage());
        }
    }

    /**
     * 定时重试失败的回传任务, 每 5 分钟执行一次
     */
    @Scheduled(cron = "0 */5 * * * ?")
    public void retryFailed() {
        List<Map<String, Object>> pending = queryPendingRetries();
        if (pending.isEmpty()) {
            return;
        }
        log.info("[HisNotify] 开始重试失败回传任务, 待重试 {} 条", pending.size());

        HisAdapter adapter;
        try {
            adapter = adapterFactory.getAdapter(defaultVendor);
        } catch (Exception e) {
            log.warn("[HisNotify] HIS 适配器不可用, 跳过重试: {}", e.getMessage());
            return;
        }

        int success = 0;
        int fail = 0;
        int dropped = 0;
        for (Map<String, Object> row : pending) {
            Long id = toLong(row.get("id"));
            String type = (String) row.get("notify_type");
            String payload = (String) row.get("payload");
            int retryCount = toInt(row.get("retry_count"));
            int maxRetry = toInt(row.get("max_retry"));
            try {
                boolean ok = dispatchRetry(adapter, type, payload);
                if (ok) {
                    markRetrySuccess(id);
                    success++;
                } else {
                    fail++;
                    markRetryFailed(id, retryCount, maxRetry, "HIS 返回失败");
                    if (retryCount + 1 >= maxRetry) {
                        dropped++;
                    }
                }
            } catch (Exception e) {
                fail++;
                markRetryFailed(id, retryCount, maxRetry, e.getMessage());
                if (retryCount + 1 >= maxRetry) {
                    dropped++;
                }
            }
        }
        log.info("[HisNotify] 重试完成: 成功 {} / 失败 {} / 放弃 {} / 总 {}", success, fail, dropped, pending.size());
    }

    // ==================== 内部方法 ====================

    private boolean dispatchRetry(HisAdapter adapter, String type, String payload) throws Exception {
        if ("critical".equals(type)) {
            CriticalValueAlert alert = objectMapper.readValue(payload, CriticalValueAlert.class);
            return adapter.sendCriticalValueAlert(alert);
        } else if ("sample-status".equals(type)) {
            SampleStatusUpdate update = objectMapper.readValue(payload, SampleStatusUpdate.class);
            return adapter.sendSampleStatus(update);
        }
        log.warn("[HisNotify] 未知重试类型: {}", type);
        return true; // 未知类型直接标记成功, 避免无限重试
    }

    private List<Map<String, Object>> queryPendingRetries() {
        try {
            String sql = "SELECT id, notify_type, payload, retry_count, max_retry "
                    + "FROM lis_his_sync_retry WHERE status = 0 "
                    + "ORDER BY create_time ASC LIMIT ?";
            return jdbcTemplate.queryForList(sql, RETRY_BATCH_SIZE);
        } catch (Exception e) {
            log.warn("[HisNotify] 查询重试表失败 (表可能不存在): {}", e.getMessage());
            return List.of();
        }
    }

    private void saveRetry(String type, String payload, String errorMsg) {
        try {
            jdbcTemplate.update(
                    "INSERT INTO lis_his_sync_retry (notify_type, payload, retry_count, max_retry, status, error_msg, create_time, update_time) "
                            + "VALUES (?, ?, 0, ?, 0, ?, ?, ?)",
                    type, payload, DEFAULT_MAX_RETRY, truncate(errorMsg, 500),
                    Timestamp.valueOf(LocalDateTime.now()), Timestamp.valueOf(LocalDateTime.now()));
        } catch (Exception e) {
            log.error("[HisNotify] 写入重试表失败 (表可能不存在): {}", e.getMessage());
        }
    }

    private void markRetrySuccess(Long id) {
        jdbcTemplate.update(
                "UPDATE lis_his_sync_retry SET status = 1, update_time = ? WHERE id = ?",
                Timestamp.valueOf(LocalDateTime.now()), id);
    }

    private void markRetryFailed(Long id, int retryCount, int maxRetry, String errorMsg) {
        int newCount = retryCount + 1;
        if (newCount >= maxRetry) {
            // 超过最大重试次数, 标记为放弃
            jdbcTemplate.update(
                    "UPDATE lis_his_sync_retry SET status = 2, retry_count = ?, error_msg = ?, update_time = ? WHERE id = ?",
                    newCount, truncate(errorMsg, 500), Timestamp.valueOf(LocalDateTime.now()), id);
        } else {
            jdbcTemplate.update(
                    "UPDATE lis_his_sync_retry SET retry_count = ?, error_msg = ?, update_time = ? WHERE id = ?",
                    newCount, truncate(errorMsg, 500), Timestamp.valueOf(LocalDateTime.now()), id);
        }
    }

    private String toJson(Object obj) {
        try {
            return objectMapper.writeValueAsString(obj);
        } catch (Exception e) {
            log.error("[HisNotify] JSON 序列化失败", e);
            return "{}";
        }
    }

    private long toLong(Object val) {
        if (val instanceof Number) {
            return ((Number) val).longValue();
        }
        return Long.parseLong(String.valueOf(val));
    }

    private int toInt(Object val) {
        if (val instanceof Number) {
            return ((Number) val).intValue();
        }
        return Integer.parseInt(String.valueOf(val));
    }

    private String truncate(String s, int maxLen) {
        if (s == null) {
            return null;
        }
        return s.length() > maxLen ? s.substring(0, maxLen) : s;
    }
}
