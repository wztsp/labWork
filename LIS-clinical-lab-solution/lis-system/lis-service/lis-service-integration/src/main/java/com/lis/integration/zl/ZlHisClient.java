package com.lis.integration.zl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lis.integration.service.HisInterfaceLogService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import javax.sql.DataSource;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * 中联新标准服务(WebAPI)通用调用客户端。
 * <p>
 * 所有 14 个服务(S1001~S4020)共用: 统一地址 his.zl.url、来源系统 his.zl.sysno、
 * 目标系统 his.zl.tarno、令牌 his.zl.token。请求报文统一为
 * {"input":{"head":{bizno,sysno,tarno,time,action_no}, ...业务参数段}}。
 * 每次调用（成功/失败）都会写入接口日志表，供前台排查。
 */
@Slf4j
@Service
public class ZlHisClient {

    private static final DateTimeFormatter TS = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    /** 业务单号提取候选字段，按优先级匹配报文中的任意层级 */
    private static final String[] REF_KEYS = {"apply_id", "spcm_bc_no", "order_id", "report_id", "pid"};

    private final JdbcTemplate jdbcTemplate;
    private final RestTemplate restTemplate = new RestTemplate();
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final HisInterfaceLogService logService;

    public ZlHisClient(DataSource dataSource, HisInterfaceLogService logService) {
        this.jdbcTemplate = new JdbcTemplate(dataSource);
        this.logService = logService;
    }

    /**
     * 是否已启用且配置了 HIS 地址
     */
    public boolean enabled() {
        return "true".equalsIgnoreCase(cfg("his.zl.enabled", "true")) && !cfg("his.zl.url", "").isEmpty();
    }

    /**
     * 调用标准服务，返回 HIS 完整应答报文；业务失败(ack_info.exe_status=E)抛异常。
     */
    public JsonNode call(String bizno, Map<String, Object> params) {
        String url = cfg("his.zl.url", "");
        if (url.isEmpty()) {
            throw new IllegalStateException("未配置 his.zl.url（HIS 数据处理平台地址）");
        }
        Map<String, Object> head = head(bizno);
        Map<String, Object> input = new LinkedHashMap<>();
        input.put("head", head);
        if (params != null) {
            input.putAll(params);
        }
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("input", input);

        String actionNo = String.valueOf(head.get("action_no"));
        String refNo = extractRefNo(params);
        String requestJson;
        try {
            requestJson = objectMapper.writeValueAsString(body);
        } catch (Exception e) {
            throw new IllegalStateException("请求报文序列化失败: " + e.getMessage(), e);
        }

        long start = System.currentTimeMillis();
        Integer httpStatus = null;
        String responseBody = null;
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            String token = cfg("his.zl.token", "");
            if (!token.isEmpty()) {
                headers.set("X-HIS-Token", token);
            }
            ResponseEntity<String> resp = restTemplate.exchange(
                    url, HttpMethod.POST, new HttpEntity<>(requestJson, headers), String.class);
            httpStatus = resp.getStatusCode().value();
            responseBody = resp.getBody();
            if (!resp.getStatusCode().is2xxSuccessful() || responseBody == null) {
                throw new IllegalStateException("HIS返回HTTP " + resp.getStatusCode());
            }
            JsonNode root = objectMapper.readTree(responseBody);
            JsonNode ack = root.path("input").path("ack_info");
            if (ack.isMissingNode()) {
                ack = root.path("output").path("ack_info");
            }
            if ("E".equalsIgnoreCase(ack.path("exe_status").asText(""))) {
                throw new IllegalStateException("HIS返回失败: " + ack.path("err_msg").asText(""));
            }
            logService.record(bizno, actionNo, 1, url, requestJson, responseBody, httpStatus,
                    true, null, System.currentTimeMillis() - start, refNo);
            log.info("[标准服务] {} 调用成功", bizno);
            return root;
        } catch (Exception e) {
            logService.record(bizno, actionNo, 1, url, requestJson, responseBody, httpStatus,
                    false, e.getMessage(), System.currentTimeMillis() - start, refNo);
            if (e instanceof IllegalStateException ise) {
                throw ise;
            }
            throw new IllegalStateException("调用HIS服务 " + bizno + " 失败: " + e.getMessage(), e);
        }
    }

    /**
     * 读取系统参数
     */
    public String cfg(String key, String defaultValue) {
        try {
            java.util.List<String> values = jdbcTemplate.queryForList(
                    "SELECT config_value FROM sys_config WHERE config_key = ? LIMIT 1", String.class, key);
            if (values.isEmpty() || values.get(0) == null) {
                return defaultValue;
            }
            return values.get(0).trim();
        } catch (Exception e) {
            return defaultValue;
        }
    }

    private Map<String, Object> head(String bizno) {
        Map<String, Object> head = new LinkedHashMap<>();
        head.put("bizno", bizno);
        head.put("sysno", cfg("his.zl.sysno", "LIS"));
        head.put("tarno", cfg("his.zl.tarno", "ZLHIS"));
        head.put("time", LocalDateTime.now().format(TS));
        head.put("action_no", UUID.randomUUID().toString());
        return head;
    }

    /**
     * 从业务参数中提取一个可读的业务单号（申请号/条码/医嘱号等），仅用于日志排查
     */
    private String extractRefNo(Map<String, Object> params) {
        return firstValue(params, 0);
    }

    private String firstValue(Object node, int depth) {
        if (node == null || depth > 4) {
            return "";
        }
        if (node instanceof Map<?, ?> map) {
            for (String key : REF_KEYS) {
                Object value = map.get(key);
                if (value != null && !String.valueOf(value).trim().isEmpty()) {
                    return String.valueOf(value).trim();
                }
            }
            for (Object value : map.values()) {
                String found = firstValue(value, depth + 1);
                if (!found.isEmpty()) {
                    return found;
                }
            }
        } else if (node instanceof Collection<?> list) {
            for (Object value : list) {
                String found = firstValue(value, depth + 1);
                if (!found.isEmpty()) {
                    return found;
                }
            }
        }
        return "";
    }
}
