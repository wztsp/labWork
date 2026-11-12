package com.lis.reagent.thirdparty.support;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lis.common.core.exception.BusinessException;
import com.lis.reagent.thirdparty.entity.ThirdPartyConfig;
import com.lis.reagent.thirdparty.entity.ThirdPartyLog;
import com.lis.reagent.mapper.ThirdPartyLogMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Base64;

/**
 * 三方卫材通用 HTTP 客户端。
 *
 * <p>鉴权方式由对接配置决定：0无 / 1Token / 2AK-SK签名 / 3Basic，
 * 每次调用统一写调用日志，失败按配置次数重试。</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ThirdPartyHttpClient {

    /** 日志正文最大保留长度，避免超长报文撑爆表 */
    private static final int MAX_BODY = 20000;

    private final ObjectMapper objectMapper;
    private final ThirdPartyLogMapper logMapper;

    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    /**
     * 发起 JSON 调用并返回解析后的响应体。
     *
     * @param cfg        对接配置
     * @param bizType    业务类型（ORDER/PRODUCT/SUPPLIER/STOCK/DELIVERY）
     * @param method     GET/POST/PUT
     * @param path       接口路径（相对路径拼 baseUrl；以 http 开头则直接使用）
     * @param body       请求体，GET 可为 null
     * @param bizNo      关联业务单号，可为空
     * @param operatorName 操作人，可为空
     * @return 响应 JSON；响应为空时返回 null
     */
    public JsonNode call(ThirdPartyConfig cfg, String bizType, String method, String path,
                         Object body, String bizNo, String operatorName) {
        String url = resolveUrl(cfg, path);
        String requestBody = body == null ? null : toJson(body);
        int attempts = Math.max(1, (cfg.getRetryTimes() == null ? 0 : cfg.getRetryTimes()) + 1);
        String lastError = null;
        long start = System.currentTimeMillis();
        for (int i = 1; i <= attempts; i++) {
            try {
                HttpResponse<String> response = send(cfg, method, url, requestBody);
                String responseBody = response.body();
                boolean success = response.statusCode() >= 200 && response.statusCode() < 300;
                writeLog(cfg.getPartnerCode(), bizType, 1, method, url, requestBody, responseBody,
                        response.statusCode(), success, bizNo, success ? null : "HTTP " + response.statusCode(),
                        System.currentTimeMillis() - start, operatorName);
                if (!success) {
                    lastError = "HTTP " + response.statusCode() + ": " + abbreviate(responseBody);
                    continue;
                }
                return blank(responseBody) ? null : objectMapper.readTree(responseBody);
            } catch (Exception e) {
                lastError = e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
                log.warn("[三方卫材调用失败] partner={}, biz={}, url={}, 第{}次, err={}",
                        cfg.getPartnerCode(), bizType, url, i, lastError);
            }
        }
        writeLog(cfg.getPartnerCode(), bizType, 1, method, url, requestBody, null,
                null, false, bizNo, lastError, System.currentTimeMillis() - start, operatorName);
        throw new BusinessException("调用" + cfg.getPartnerName() + "接口失败: " + lastError);
    }

    /** 仅做连通性测试，返回原始响应文本。 */
    public String ping(ThirdPartyConfig cfg, String operatorName) {
        String url = cfg.getBaseUrl();
        long start = System.currentTimeMillis();
        try {
            HttpResponse<String> response = send(cfg, "GET", url, null);
            writeLog(cfg.getPartnerCode(), "TEST", 1, "GET", url, null, response.body(),
                    response.statusCode(), response.statusCode() >= 200 && response.statusCode() < 300,
                    null, null, System.currentTimeMillis() - start, operatorName);
            return "HTTP " + response.statusCode() + " " + abbreviate(response.body());
        } catch (Exception e) {
            writeLog(cfg.getPartnerCode(), "TEST", 1, "GET", url, null, null, null, false,
                    null, e.getMessage(), System.currentTimeMillis() - start, operatorName);
            throw new BusinessException("连接测试失败: " + e.getMessage());
        }
    }

    /** 入站请求留痕（对方调用我们）。 */
    public void logInbound(String partnerCode, String bizType, String method, String url,
                           String requestBody, String responseBody, boolean success,
                           String bizNo, String errorMsg) {
        writeLog(partnerCode, bizType, 2, method, url, requestBody, responseBody,
                success ? 200 : 400, success, bizNo, errorMsg, null, null);
    }

    private HttpResponse<String> send(ThirdPartyConfig cfg, String method, String url, String body) throws Exception {
        HttpRequest.Builder builder = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(Duration.ofMillis(cfg.getTimeoutMs() == null ? 10000 : cfg.getTimeoutMs()));
        if (body != null) {
            builder.header("Content-Type", "application/json; charset=UTF-8");
        }
        applyAuth(cfg, builder, method, url, body == null ? "" : body);
        if ("GET".equalsIgnoreCase(method)) {
            builder.GET();
        } else if ("POST".equalsIgnoreCase(method)) {
            builder.POST(HttpRequest.BodyPublishers.ofString(body == null ? "" : body, StandardCharsets.UTF_8));
        } else {
            builder.method(method, HttpRequest.BodyPublishers.ofString(body == null ? "" : body, StandardCharsets.UTF_8));
        }
        return httpClient.send(builder.build(), HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
    }

    private void applyAuth(ThirdPartyConfig cfg, HttpRequest.Builder builder, String method, String url, String body)
            throws Exception {
        int authType = cfg.getAuthType() == null ? 0 : cfg.getAuthType();
        if (authType == 1 && !blank(cfg.getAuthToken())) {
            String header = blank(cfg.getAuthHeader()) ? "Authorization" : cfg.getAuthHeader().trim();
            builder.header(header, cfg.getAuthToken());
        } else if (authType == 2 && !blank(cfg.getAppKey())) {
            String timestamp = String.valueOf(System.currentTimeMillis());
            String path = URI.create(url).getPath();
            String query = URI.create(url).getRawQuery();
            String signText = method + "\n" + path + (query == null ? "" : "?" + query)
                    + "\n" + timestamp + "\n" + body;
            builder.header("X-App-Key", cfg.getAppKey());
            builder.header("X-Timestamp", timestamp);
            builder.header("X-Sign", hmacSha256(cfg.getAppSecret(), signText));
        } else if (authType == 3 && !blank(cfg.getBasicUsername())) {
            String raw = cfg.getBasicUsername() + ":" + (cfg.getBasicPassword() == null ? "" : cfg.getBasicPassword());
            builder.header("Authorization",
                    "Basic " + Base64.getEncoder().encodeToString(raw.getBytes(StandardCharsets.UTF_8)));
        }
    }

    private String hmacSha256(String secret, String text) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec((secret == null ? "" : secret).getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        return Base64.getEncoder().encodeToString(mac.doFinal(text.getBytes(StandardCharsets.UTF_8)));
    }

    private String resolveUrl(ThirdPartyConfig cfg, String path) {
        if (path != null && (path.startsWith("http://") || path.startsWith("https://"))) {
            return path;
        }
        String base = cfg.getBaseUrl() == null ? "" : cfg.getBaseUrl().trim();
        if (base.endsWith("/")) {
            base = base.substring(0, base.length() - 1);
        }
        String suffix = path == null ? "" : path.trim();
        if (!suffix.isEmpty() && !suffix.startsWith("/")) {
            suffix = "/" + suffix;
        }
        return base + suffix;
    }

    private void writeLog(String partnerCode, String bizType, int direction, String method, String url,
                          String requestBody, String responseBody, Integer httpStatus, boolean success,
                          String bizNo, String errorMsg, Long costMs, String operatorName) {
        try {
            ThirdPartyLog record = new ThirdPartyLog();
            record.setPartnerCode(partnerCode);
            record.setBizType(bizType);
            record.setDirection(direction);
            record.setMethod(method);
            record.setUrl(url);
            record.setRequestBody(abbreviate(requestBody));
            record.setResponseBody(abbreviate(responseBody));
            record.setHttpStatus(httpStatus);
            record.setSuccess(success ? 1 : 0);
            record.setBizNo(bizNo);
            record.setErrorMsg(abbreviate(errorMsg));
            record.setCostMs(costMs);
            record.setOperatorName(operatorName);
            record.setCreateTime(LocalDateTime.now());
            logMapper.insert(record);
        } catch (Exception e) {
            // 日志失败不能影响主流程
            log.warn("[三方卫材日志写入失败] {}", e.getMessage());
        }
    }

    private String toJson(Object body) {
        try {
            return objectMapper.writeValueAsString(body);
        } catch (Exception e) {
            throw new BusinessException("请求体序列化失败: " + e.getMessage());
        }
    }

    private static String abbreviate(String text) {
        if (text == null) {
            return null;
        }
        return text.length() > MAX_BODY ? text.substring(0, MAX_BODY) + "...(截断)" : text;
    }

    private static boolean blank(String text) {
        return text == null || text.trim().isEmpty();
    }
}
