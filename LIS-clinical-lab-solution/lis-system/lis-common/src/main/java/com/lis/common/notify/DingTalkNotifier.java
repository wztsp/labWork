package com.lis.common.notify;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 钉钉通知器
 * <p>
 * 通过钉钉群机器人 webhook 发送告警
 */
@Slf4j
@Component
public class DingTalkNotifier implements NotifyService {

    private final RestTemplate restTemplate = new RestTemplate();

    @Value("${lis.notify.dingtalk-webhook:}")
    private String webhookUrl;

    @Override
    public void sendAlert(String title, String content, List<String> recipients) {
        if (webhookUrl == null || webhookUrl.isBlank()) {
            log.warn("钉钉 webhook 未配置，跳过通知: title={}", title);
            return;
        }
        try {
            StringBuilder md = new StringBuilder();
            md.append("### ").append(title).append("\n\n")
                    .append(content).append("\n\n");
            if (recipients != null && !recipients.isEmpty()) {
                md.append("**通知对象**: ").append(String.join(",", recipients)).append("\n");
            }

            Map<String, Object> payload = new HashMap<>();
            payload.put("msgtype", "markdown");
            Map<String, String> markdown = new HashMap<>();
            markdown.put("title", title);
            markdown.put("text", md.toString());
            payload.put("markdown", markdown);

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(payload, headers);

            String resp = restTemplate.postForObject(webhookUrl, entity, String.class);
            log.info("钉钉通知已发送: title={}, resp={}", title, resp);
        } catch (Exception e) {
            log.error("钉钉通知发送失败: title={}", title, e);
        }
    }
}
