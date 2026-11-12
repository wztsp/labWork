package com.lis.sample.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.MissingNode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * HIS 入站接口日志（HIS 调 LIS）。
 * <p>
 * 与出站调用共用 lis_his_zl_log 表，direction=2；日志写入失败只打 warn，不影响接口主流程。
 * 只在 HTTP 入站处记录：S4020 拉取申请走的是进程内调用，不算入站。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class HisInboundLogService {

    /** 报文正文最大保留长度 */
    private static final int MAX_BODY = 8000;
    private static final int MAX_ERROR = 1000;
    /** 业务单号提取候选字段，按优先级匹配报文中的任意层级 */
    private static final String[] REF_KEYS = {"apply_id", "spcm_bc_no", "order_id", "report_id", "pid"};
    private static final String INSERT_SQL =
            "INSERT INTO lis_his_zl_log (service_no, action_no, direction, url, request_body, response_body,"
                    + " http_status, success, error_msg, cost_ms, ref_no, create_time)"
                    + " VALUES (?, ?, 2, ?, ?, ?, 200, ?, ?, ?, ?, ?)";
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final JdbcTemplate jdbcTemplate;

    /**
     * 记录一次入站调用。
     *
     * @param url               接口地址
     * @param request           请求报文，可为 null
     * @param result            接口应答（含 ack_info）
     * @param fallbackServiceNo 报文未带 head.bizno 时使用的服务编号
     * @param startMs           处理开始时间戳（毫秒）
     */
    public void record(String url, JsonNode request, Map<String, Object> result,
                       String fallbackServiceNo, long startMs) {
        try {
            JsonNode head = head(request);
            String serviceNo = firstNonEmpty(head.path("bizno").asText(""), fallbackServiceNo);
            Map<String, Object> ack = ackInfo(result, 0);
            boolean success = ack != null && "A".equalsIgnoreCase(String.valueOf(ack.get("exe_status")));
            String errorMsg = success ? null
                    : (ack == null ? "应答缺少 ack_info" : String.valueOf(ack.get("err_msg")));

            jdbcTemplate.update(INSERT_SQL,
                    truncate(serviceNo, 16),
                    truncate(head.path("action_no").asText(""), 64),
                    truncate(url, 500),
                    truncate(request == null || request.isNull() ? null : request.toString(), MAX_BODY),
                    truncate(MAPPER.writeValueAsString(result), MAX_BODY),
                    success ? 1 : 0,
                    truncate(errorMsg, MAX_ERROR),
                    System.currentTimeMillis() - startMs,
                    truncate(extractRefNo(request, 0), 200),
                    LocalDateTime.now());
        } catch (Exception e) {
            log.warn("[HIS接口日志] 入站日志写入失败: {}", e.getMessage());
        }
    }

    /** 兼容 {"input":{"head":...}} 与直接 {"head":...} 两种报文 */
    private JsonNode head(JsonNode request) {
        if (request == null || request.isNull()) {
            return MissingNode.getInstance();
        }
        JsonNode input = request.path("input");
        return (input.isObject() ? input : request).path("head");
    }

    /** 在应答中递归找 ack_info */
    @SuppressWarnings("unchecked")
    private Map<String, Object> ackInfo(Object node, int depth) {
        if (!(node instanceof Map<?, ?> map) || depth > 3) {
            return null;
        }
        Object ack = map.get("ack_info");
        if (ack instanceof Map<?, ?> ackMap) {
            return (Map<String, Object>) ackMap;
        }
        for (Object value : map.values()) {
            Map<String, Object> found = ackInfo(value, depth + 1);
            if (found != null) {
                return found;
            }
        }
        return null;
    }

    /** 从请求报文中提取一个可读的业务单号（申请号/条码/医嘱号等），仅用于日志排查 */
    private String extractRefNo(JsonNode node, int depth) {
        if (node == null || depth > 4) {
            return "";
        }
        if (node.isObject()) {
            for (String key : REF_KEYS) {
                String value = node.path(key).asText("").trim();
                if (!value.isEmpty()) {
                    return value;
                }
            }
        }
        for (JsonNode child : node) {
            String found = extractRefNo(child, depth + 1);
            if (!found.isEmpty()) {
                return found;
            }
        }
        return "";
    }

    private String firstNonEmpty(String first, String second) {
        return first == null || first.trim().isEmpty() ? second : first.trim();
    }

    private String truncate(String text, int maxLength) {
        if (text == null) {
            return null;
        }
        return text.length() > maxLength ? text.substring(0, maxLength) + "...(截断)" : text;
    }
}
