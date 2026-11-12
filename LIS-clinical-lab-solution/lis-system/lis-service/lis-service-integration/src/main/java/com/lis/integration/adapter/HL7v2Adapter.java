package com.lis.integration.adapter;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lis.api.dto.OrderDTO;
import com.lis.api.dto.OrderItemDTO;
import com.lis.integration.dto.CriticalValueAlert;
import com.lis.integration.dto.FeeBilling;
import com.lis.integration.dto.LabReport;
import com.lis.integration.dto.PatientInfo;
import com.lis.integration.dto.SampleStatusUpdate;
import com.lis.integration.message.HL7MessageBuilder;
import com.lis.integration.message.HL7MessageParser;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.Socket;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;

/**
 * HL7 v2 适配器
 * <p>
 * 通过 TCP 发送/接收 HL7 v2 消息 (MLLP 包装)。
 * <p>
 * 申请同步与健康检查采用 REST 方式 (比 HL7 更实用):
 * <ul>
 *     <li>{@link #receiveOrders(Date, Date)}: GET {his.url}/api/his/orders 拉取未同步申请,
 *         解析 JSON 转换为 {@link OrderDTO}, 并调用标本服务 /api/sample/orders/receive 落库</li>
 *     <li>{@link #healthCheck()}: GET {his.url}/api/his/health</li>
 * </ul>
 * 结果回传/危急值/标本状态仍使用 HL7 ORU^R01 TCP 通道。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class HL7v2Adapter implements HisAdapter {

    private final HL7MessageBuilder messageBuilder;
    private final HL7MessageParser messageParser;

    @Value("${integration.his.vendors.HL7V2.host:his.example.com}")
    private String host;
    @Value("${integration.his.vendors.HL7V2.port:2575}")
    private int port;

    /** HIS REST 服务地址 (用于 REST 方式同步申请与健康检查) */
    @Value("${his.url:http://localhost:8099}")
    private String hisUrl;
    /** HIS 对接开关 (默认关闭, 避免 HIS 不可用时同步报错) */
    @Value("${his.enabled:false}")
    private boolean hisEnabled;
    /** 标本服务地址 (用于 receiveOrders 后调用 /api/sample/orders/receive) */
    @Value("${his.sample-service-url:http://localhost:8081}")
    private String sampleServiceUrl;

    private final RestTemplate restTemplate = new RestTemplate();
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public String getAdapterId() {
        return "HL7V2";
    }

    @Override
    public boolean healthCheck() {
        // REST 方式: GET {his.url}/api/his/health
        if (!hisEnabled) {
            log.debug("[HL7v2Adapter] HIS 对接未启用 (his.enabled=false), 跳过健康检查");
            return false;
        }
        try {
            HttpHeaders headers = buildJsonHeaders();
            ResponseEntity<String> resp = restTemplate.exchange(
                    hisUrl + "/api/his/health", HttpMethod.GET,
                    new HttpEntity<>(headers), String.class);
            boolean ok = resp.getStatusCode().is2xxSuccessful();
            log.info("[HL7v2Adapter] HIS 健康检查: {} ({})", ok ? "成功" : "失败", resp.getStatusCode());
            return ok;
        } catch (Exception e) {
            log.warn("[HL7v2Adapter] HIS 健康检查失败: {}", e.getMessage());
            return false;
        }
    }

    @Override
    public List<OrderDTO> receiveOrders(Date startTime, Date endTime) {
        if (!hisEnabled) {
            log.debug("[HL7v2Adapter] HIS 对接未启用 (his.enabled=false), 跳过申请同步");
            return Collections.emptyList();
        }
        // REST 方式同步: GET {his.url}/api/his/orders?start={startTime}&end={endTime}
        List<OrderDTO> orders = fetchOrdersFromHis(startTime, endTime);
        if (orders.isEmpty()) {
            log.info("[HL7v2Adapter] 本次无新增 HIS 申请");
            return orders;
        }
        log.info("[HL7v2Adapter] 从 HIS 拉取到 {} 条申请, 开始下发标本服务", orders.size());
        // 调用标本服务 /api/sample/orders/receive 逐条落库
        int success = 0;
        for (OrderDTO order : orders) {
            try {
                forwardToSampleService(order);
                success++;
            } catch (Exception e) {
                log.error("[HL7v2Adapter] 下发标本服务失败: orderNo={}", order.getOrderNo(), e);
            }
        }
        log.info("[HL7v2Adapter] 申请同步完成: 成功 {} / 总 {}", success, orders.size());
        return orders;
    }

    /**
     * GET {his.url}/api/his/orders 获取未同步申请, 解析 JSON 转换为 {@link OrderDTO}
     */
    private List<OrderDTO> fetchOrdersFromHis(Date startTime, Date endTime) {
        try {
            HttpHeaders headers = buildJsonHeaders();
            String url = hisUrl + "/api/his/orders?start=" + startTime.getTime()
                    + "&end=" + endTime.getTime();
            ResponseEntity<String> resp = restTemplate.exchange(
                    url, HttpMethod.GET, new HttpEntity<>(headers), String.class);
            if (!resp.getStatusCode().is2xxSuccessful() || resp.getBody() == null) {
                log.warn("[HL7v2Adapter] HIS 返回非 2xx: {}", resp.getStatusCode());
                return Collections.emptyList();
            }
            return parseOrders(resp.getBody());
        } catch (Exception e) {
            log.error("[HL7v2Adapter] 拉取 HIS 申请失败", e);
            return Collections.emptyList();
        }
    }

    /**
     * 解析 HIS 返回的 JSON, 转换为 {@link OrderDTO} 列表
     * 兼容两种格式: 直接数组 或 {"data":[...]} / {"records":[...]}
     */
    private List<OrderDTO> parseOrders(String json) {
        try {
            JsonNode root = objectMapper.readTree(json);
            JsonNode arr = root;
            if (root.isObject()) {
                // 兼容 R<List> 包装: data / records / list
                if (root.has("data")) {
                    arr = root.get("data");
                } else if (root.has("records")) {
                    arr = root.get("records");
                } else if (root.has("list")) {
                    arr = root.get("list");
                }
            }
            if (arr == null || !arr.isArray()) {
                log.warn("[HL7v2Adapter] HIS 返回 JSON 非数组格式: {}", json.substring(0, Math.min(120, json.length())));
                return Collections.emptyList();
            }
            List<OrderDTO> orders = new ArrayList<>();
            for (JsonNode node : arr) {
                OrderDTO order = parseOrderNode(node);
                if (order != null) {
                    orders.add(order);
                }
            }
            return orders;
        } catch (Exception e) {
            log.error("[HL7v2Adapter] 解析 HIS 申请 JSON 失败", e);
            return Collections.emptyList();
        }
    }

    private OrderDTO parseOrderNode(JsonNode node) {
        try {
            OrderDTO.OrderDTOBuilder b = OrderDTO.builder();
            b.orderNo(textOr(node, "orderNo", textOr(node, "order_no", null)));
            b.visitId(longOr(node, "visitId", longOr(node, "visit_id", null)));
            b.patientId(longOr(node, "patientId", longOr(node, "patient_id", null)));
            b.patientName(textOr(node, "patientName", textOr(node, "patient_name", null)));
            b.orderType(textOr(node, "orderType", textOr(node, "order_type", null)));
            b.applyDoctor(textOr(node, "applyDoctor", textOr(node, "apply_doctor", null)));
            b.applyDept(textOr(node, "applyDept", textOr(node, "apply_dept", null)));
            b.clinicalDiag(textOr(node, "clinicalDiag", textOr(node, "clinical_diag", null)));
            b.patientCondition(textOr(node, "patientCondition", textOr(node, "patient_condition", null)));
            // 解析申请项目列表
            JsonNode itemsNode = node.has("items") ? node.get("items") : node.get("itemList");
            if (itemsNode != null && itemsNode.isArray()) {
                List<OrderItemDTO> items = new ArrayList<>();
                for (JsonNode itemNode : itemsNode) {
                    items.add(OrderItemDTO.builder()
                            .testItemId(longOr(itemNode, "testItemId", longOr(itemNode, "test_item_id", null)))
                            .testItemCode(textOr(itemNode, "testItemCode", textOr(itemNode, "test_item_code", null)))
                            .testItemName(textOr(itemNode, "testItemName", textOr(itemNode, "test_item_name", null)))
                            .build());
                }
                b.items(items);
            }
            return b.build();
        } catch (Exception e) {
            log.warn("[HL7v2Adapter] 解析申请节点失败: {}", e.getMessage());
            return null;
        }
    }

    /**
     * 调用标本服务 /api/sample/orders/receive 接口落库
     */
    private void forwardToSampleService(OrderDTO order) {
        HttpHeaders headers = buildJsonHeaders();
        HttpEntity<OrderDTO> entity = new HttpEntity<>(order, headers);
        ResponseEntity<String> resp = restTemplate.exchange(
                sampleServiceUrl + "/api/sample/orders/receive",
                HttpMethod.POST, entity, String.class);
        if (!resp.getStatusCode().is2xxSuccessful()) {
            log.warn("[HL7v2Adapter] 标本服务返回非 2xx: orderNo={}, status={}",
                    order.getOrderNo(), resp.getStatusCode());
        }
    }

    private HttpHeaders buildJsonHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.setAccept(Collections.singletonList(MediaType.APPLICATION_JSON));
        headers.setContentType(MediaType.APPLICATION_JSON);
        return headers;
    }

    private String textOr(JsonNode node, String key1, String key2) {
        if (node.has(key1) && !node.get(key1).isNull()) {
            return node.get(key1).asText();
        }
        if (key2 != null && node.has(key2) && !node.get(key2).isNull()) {
            return node.get(key2).asText();
        }
        return null;
    }

    private Long longOr(JsonNode node, String key1, Long fallback) {
        if (node.has(key1) && !node.get(key1).isNull()) {
            return node.get(key1).asLong();
        }
        return fallback;
    }

    @Override
    public PatientInfo queryPatient(String patientId, String visitId) {
        if (!hisEnabled) {
            log.debug("[HL7v2Adapter] HIS 对接未启用 (his.enabled=false), 跳过患者查询");
            return null;
        }
        if (patientId == null || patientId.isEmpty()) {
            // 无患者主键不发报文, 直接降级
            log.debug("[HL7v2Adapter] patientId 为空, 不发送查询报文");
            return null;
        }
        // 1. 构造 QBP^Q21 并经由 MLLP/TCP 发送
        String qbp = messageBuilder.buildQBP_Q21(patientId, visitId);
        String ack = sendAndReceive(qbp);
        // 2. 无响应 (超时/连接失败) → 降级返回 null
        if (ack == null || ack.isBlank()) {
            log.warn("[HL7v2Adapter] HIS 患者查询无响应 (超时/连接失败): patientId={}", patientId);
            return null;
        }
        // 3. HIS 拒绝/报错 (MSA|AE / MSA|AR) → 降级返回 null
        if (ack.contains("MSA|AE") || ack.contains("MSA|AR")) {
            log.warn("[HL7v2Adapter] HIS 拒绝患者查询 (MSA AE/AR): patientId={}", patientId);
            return null;
        }
        // 4. 解析 RSP^K21 响应; 解析异常兜底返回 null
        try {
            PatientInfo info = messageParser.parseRSP_K21(ack);
            if (info != null && visitId != null && !visitId.isEmpty()) {
                info.setVisitId(visitId);
            }
            return info;
        } catch (Exception e) {
            log.warn("[HL7v2Adapter] 解析 HIS 患者响应失败: patientId={}", patientId, e);
            return null;
        }
    }

    @Override
    public boolean sendResults(LabReport report) {
        String hl7 = messageBuilder.buildORU_R01(report);
        return sendHl7(hl7);
    }

    @Override
    public boolean sendSampleStatus(SampleStatusUpdate update) {
        // 简化: 标本状态变更可通过 ORU 或自定义消息发送
        String hl7 = "MSH|^~\\&|LIS|LAB|HIS|HOSP|" + System.currentTimeMillis()
                + "||ORU^R01|STATUS" + update.getOrderNo() + "|P|2.5\r"
                + "OBX|1|ST|SAMPLE_STATUS||" + update.getStatus() + "||||||F";
        return sendHl7(hl7);
    }

    @Override
    public boolean sendCriticalValueAlert(CriticalValueAlert alert) {
        String hl7 = "MSH|^~\\&|LIS|LAB|HIS|HOSP|" + System.currentTimeMillis()
                + "||ORU^R01|CRIT" + alert.getPatientId() + "|P|2.5\r"
                + "OBX|1|NM|^^^" + alert.getItemCode() + "|1|"
                + alert.getResultValue() + "|" + alert.getUnit() + "|" + alert.getRefRange()
                + "|H||||F";
        return sendHl7(hl7);
    }

    @Override
    public boolean sendFeeBilling(FeeBilling billing) {
        // 简化: 通过自定义 RAS^R01 消息发送费用
        StringBuilder hl7 = new StringBuilder();
        hl7.append("MSH|^~\\&|LIS|LAB|HIS|HOSP|").append(System.currentTimeMillis())
                .append("||RAS^R01|FEE").append(billing.getOrderId()).append("|P|2.5\r");
        hl7.append("PID|||").append(billing.getPatientId()).append("||||||||||||||||||\r");
        if (billing.getItems() != null) {
            int idx = 1;
            for (var item : billing.getItems()) {
                hl7.append("OBX|").append(idx).append("|NM|^^^").append(item.getTestItemId())
                        .append("|").append(idx).append("|").append(item.getFee())
                        .append("|CNY|||F\r");
                idx++;
            }
        }
        hl7.append("FT1|||").append(billing.getOrderId()).append("|||").append(billing.getTotalFee())
                .append("|CNY\r");
        return sendHl7(hl7.toString());
    }

    /**
     * 通过 MLLP/ TCP 发送 HL7 消息
     */
    private boolean sendHl7(String hl7) {
        try {
            String ack = sendAndReceive(hl7);
            return ack != null;
        } catch (Exception e) {
            log.error("HL7 发送失败", e);
            return false;
        }
    }

    /**
     * 通过 MLLP/TCP 发送 HL7 消息并读取对端响应。
     * @return 对端返回的 HL7 响应 (已去除 MLLP 包装); 失败返回 null
     */
    private String sendAndReceive(String hl7) {
        try (Socket socket = new Socket(host, port)) {
            socket.setSoTimeout(10000);
            OutputStream out = socket.getOutputStream();
            BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            // MLLP 包装: <SB> + message + <EB><CR>
            String mllp = "\u000b" + hl7 + "\u001c\r";
            out.write(mllp.getBytes());
            out.flush();
            StringBuilder resp = new StringBuilder();
            String line;
            // 读取直到无数据 (MLLP 帧可能含多行)
            while ((line = in.readLine()) != null) {
                resp.append(line).append("\r");
            }
            String ack = resp.toString();
            // 去除 MLLP 控制字符
            ack = ack.replace("\u000b", "").replace("\u001c", "").trim();
            log.info("HL7 收到响应: {}", ack.isEmpty() ? "(empty)"
                    : ack.substring(0, Math.min(60, ack.length())));
            return ack;
        } catch (Exception e) {
            log.error("HL7 收发失败", e);
            return null;
        }
    }
}
