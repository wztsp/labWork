package com.lis.sample.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.lis.api.dto.OrderDTO;
import com.lis.api.dto.OrderItemDTO;
import com.lis.sample.entity.LabOrder;
import com.lis.sample.entity.Patient;
import com.lis.sample.entity.Visit;
import com.lis.sample.mapper.PatientMapper;
import com.lis.sample.mapper.VisitMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * HIS 检验申请接收服务。
 * <p>
 * HIS 通过 WebAPI POST 推送申请报文（input/pv1_info/apply_info/apply_item），
 * 解析后复用 {@link LabOrderService#receiveOrder} 落库，形成检验申请，
 * 在「待采集申请」页面即可看到待采集任务。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class HisApplyService {

    private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final JdbcTemplate jdbcTemplate;
    private final PatientMapper patientMapper;
    private final VisitMapper visitMapper;
    private final LabOrderService labOrderService;
    private final BarcodeService barcodeService;

    /**
     * 接收 HIS 申请报文，返回 HIS 平台应答格式 {"output":{"ack_info":{...}}}
     */
    public Map<String, Object> receive(JsonNode body, String token) {
        try {
            if (!"true".equalsIgnoreCase(cfg("his.apply.enabled", "true"))) {
                return ack("E", "HIS检验申请接口未启用（参数 his.apply.enabled=false）");
            }
            String needToken = cfg("his.apply.token", "");
            if (!needToken.isEmpty() && !needToken.equals(token)) {
                log.warn("[HIS申请] 令牌校验失败, token={}", token);
                return ack("E", "令牌校验失败");
            }
            if (body == null || body.isNull()) {
                return ack("E", "请求报文为空");
            }

            JsonNode input = body.path("input").isObject() ? body.path("input") : body;
            JsonNode pv1 = input.path("pv1_info");
            String patientName = txt(pv1, "pat_name");
            if (isBlank(patientName)) {
                return ack("E", "缺少患者姓名 input.pv1_info.pat_name");
            }

            String bizNo = txt(input.path("head"), "bizno");
            String actionNo = txt(input.path("head"), "action_no");
            log.info("[HIS申请] 收到报文 bizno={}, action_no={}, 患者={}", bizNo, actionNo, patientName);

            Patient patient = resolvePatient(pv1);
            Visit visit = resolveVisit(pv1, patient);
            Map<String, BigDecimal> feeMap = collectFees(input);
            Map<String, Long> itemCache = new HashMap<>();
            boolean autoCreateItem = "true".equalsIgnoreCase(cfg("his.apply.auto-create-item", "true"));
            String defaultDept = cfg("his.apply.default-dept", "门诊");

            int created = 0;
            int skipped = 0;
            List<String> errors = new ArrayList<>();

            for (JsonNode apply : input.path("apply_info")) {
                String applyId = first(txt(apply, "apply_id"), actionNo);
                String dept = first(txt(apply, "apply_dept_name"), txt(pv1, "dept_name"), defaultDept);
                String doctor = txt(apply, "placer_name");
                String collectMethod = txt(apply, "citem_name");
                String specimenType = txt(apply, "lspcm_name");
                String diagnosis = stripDiag(first(addition(apply, "临床诊断"), dzContent(input),
                        txt(apply, "order_content")));
                String patientCondition = addition(apply, "生理周期");
                LocalDateTime applyTime = parseTime(txt(apply, "apply_time"));
                Long methodId = findCollectionMethodId(collectMethod);
                String orderType = resolveOrderType(pv1, apply);

                JsonNode items = apply.path("apply_item");
                if (!items.isArray() || items.isEmpty()) {
                    log.warn("[HIS申请] 申请 {} 没有明细项目, 跳过", applyId);
                    continue;
                }

                for (JsonNode itemNode : items) {
                    String itemCode = txt(itemNode, "citem_code");
                    String itemName = txt(itemNode, "citem_name");
                    String hisKey = applyId + "#" + first(txt(itemNode, "order_id"),
                            txt(itemNode, "citem_id"), itemCode, itemName);
                    if (hisKey.length() > 80) {
                        hisKey = hisKey.substring(0, 80);
                    }

                    if (existsHisApplyId(hisKey)) {
                        skipped++;
                        continue;
                    }

                    try {
                        Long testItemId = resolveTestItem(itemCode, itemName, specimenType, autoCreateItem, itemCache);
                        OrderDTO dto = new OrderDTO();
                        dto.setPatientId(patient.getId());
                        dto.setVisitId(visit.getId());
                        dto.setPatientName(patient.getName());
                        dto.setOrderType(orderType);
                        dto.setApplyDept(dept);
                        dto.setApplyDoctor(doctor);
                        dto.setClinicalDiag(diagnosis);
                        dto.setPatientCondition(patientCondition);
                        dto.setCollectionMethodId(methodId);
                        dto.setCollectionMethodName(collectMethod);

                        OrderItemDTO item = OrderItemDTO.builder()
                                .testItemId(testItemId)
                                .testItemCode(itemCode)
                                .testItemName(itemName)
                                .fee(feeMap.get(txt(itemNode, "order_id")))
                                .hisApplyId(hisKey)
                                .build();
                        dto.setItems(Collections.singletonList(item));
                        labOrderService.receiveOrder(dto);
                        created++;
                    } catch (DuplicateKeyException e) {
                        // 并发重复推送, 唯一索引兜底
                        skipped++;
                    } catch (Exception e) {
                        log.error("[HIS申请] 申请 {} 项目 {} 落库失败", applyId, itemName, e);
                        errors.add(applyId + "/" + itemName + ": " + e.getMessage());
                    }
                }
            }

            log.info("[HIS申请] 处理完成 bizno={}: 新增 {} 条, 重复跳过 {} 条, 失败 {} 条",
                    bizNo, created, skipped, errors.size());
            if (!errors.isEmpty()) {
                String msg = "成功" + created + "条, 失败" + errors.size() + "条; " + errors.get(0);
                return ack("E", msg.length() > 200 ? msg.substring(0, 200) : msg);
            }
            return ack("A", "");
        } catch (IllegalArgumentException e) {
            log.warn("[HIS申请] 报文校验失败: {}", e.getMessage());
            return ack("E", e.getMessage());
        } catch (Exception e) {
            log.error("[HIS申请] 处理失败", e);
            return ack("E", "处理失败: " + e.getMessage());
        }
    }

    /**
     * HIS 撤销检验申请(S4014): 按申请号+医嘱号定位 LIS 申请并作废。
     */
    public Map<String, Object> cancel(JsonNode body, String token) {
        try {
            if (!"true".equalsIgnoreCase(cfg("his.apply.enabled", "true"))) {
                return ackInput("E", "HIS检验申请接口未启用（参数 his.apply.enabled=false）", null);
            }
            String needToken = cfg("his.apply.token", "");
            if (!needToken.isEmpty() && !needToken.equals(token)) {
                return ackInput("E", "令牌校验失败", null);
            }
            if (body == null || body.isNull()) {
                return ackInput("E", "请求报文为空", null);
            }
            JsonNode input = body.path("input").isObject() ? body.path("input") : body;
            String actionNo = txt(input.path("head"), "action_no");
            log.info("[HIS撤销] 收到撤销申请 bizno={}, action_no={}",
                    txt(input.path("head"), "bizno"), actionNo);

            Map<String, Object> applyInfo = new LinkedHashMap<>();
            List<String> orderIds = new ArrayList<>();
            int canceled = 0;
            int notFound = 0;

            for (JsonNode apply : input.path("apply_info")) {
                String applyId = txt(apply, "apply_id");
                for (JsonNode item : apply.path("apply_item")) {
                    String orderId = first(txt(item, "order_id"), txt(item, "citem_id"));
                    if (!orderId.isEmpty()) {
                        orderIds.add(orderId);
                    }
                    List<LabOrder> targets = findOrders(applyId, orderId);
                    if (targets.isEmpty()) {
                        notFound++;
                        log.warn("[HIS撤销] 未找到对应申请 apply_id={}, order_id={}", applyId, orderId);
                        continue;
                    }
                    for (LabOrder order : targets) {
                        if (order.getStatus() != null && order.getStatus() == 2) {
                            continue;
                        }
                        labOrderService.cancelOrder(order.getId(), "HIS撤销检验申请");
                        canceled++;
                    }
                }
            }

            log.info("[HIS撤销] 处理完成: 作废 {} 条, 未匹配 {} 条", canceled, notFound);
            applyInfo.put("order_id", String.join(",", orderIds));
            if (orderIds.isEmpty()) {
                return ackInput("E", "缺少医嘱id(apply_item.order_id)", applyInfo);
            }
            if (canceled == 0 && notFound > 0) {
                return ackInput("E", "未找到对应的检验申请", applyInfo);
            }
            return ackInput("A", "", applyInfo);
        } catch (Exception e) {
            log.error("[HIS撤销] 处理失败", e);
            return ackInput("E", "处理失败: " + e.getMessage(), null);
        }
    }

    /**
     * 按 HIS 申请号+医嘱号定位申请; 精确匹配不到时按申请号前缀兜底
     */
    private List<LabOrder> findOrders(String applyId, String orderId) {
        if (applyId.isEmpty()) {
            return Collections.emptyList();
        }
        if (!orderId.isEmpty()) {
            List<LabOrder> exact = labOrderService.list(new LambdaQueryWrapper<LabOrder>()
                    .eq(LabOrder::getHisApplyId, applyId + "#" + orderId));
            if (!exact.isEmpty()) {
                return exact;
            }
        }
        return labOrderService.list(new LambdaQueryWrapper<LabOrder>()
                .likeRight(LabOrder::getHisApplyId, applyId + "#"));
    }

    /**
     * 患者按 HIS 患者号(pid)定位，不存在则建档
     */
    private Patient resolvePatient(JsonNode pv1) {
        String pid = txt(pv1, "pid");
        Patient patient = null;
        if (!pid.isEmpty()) {
            patient = patientMapper.selectOne(new LambdaQueryWrapper<Patient>()
                    .eq(Patient::getPatientNo, pid).last("LIMIT 1"));
        }
        if (patient != null) {
            return patient;
        }
        patient = new Patient();
        patient.setPatientNo(isBlank(pid) ? barcodeService.generateBarcode("PATIENT") : pid);
        patient.setName(txt(pv1, "pat_name"));
        patient.setGender(gender(txt(pv1, "pat_sex")));
        patient.setBirthDate(parseDate(txt(pv1, "pat_brsdate")));
        patient.setAge(parseAge(txt(pv1, "pat_age")));
        patient.setAddress(txt(pv1, "pat_home_addr"));
        patient.setCreateTime(LocalDateTime.now());
        patient.setUpdateTime(LocalDateTime.now());
        patientMapper.insert(patient);
        return patient;
    }

    /**
     * 就诊按门诊号/住院号/pvid 定位，不存在则新建
     */
    private Visit resolveVisit(JsonNode pv1, Patient patient) {
        String visitNo = first(txt(pv1, "outpno"), txt(pv1, "inpno"), txt(pv1, "pvid"));
        if (isBlank(visitNo)) {
            visitNo = barcodeService.generateBarcode("VISIT");
        }
        Visit visit = visitMapper.selectOne(new LambdaQueryWrapper<Visit>()
                .eq(Visit::getVisitNo, visitNo).last("LIMIT 1"));
        if (visit != null) {
            return visit;
        }
        visit = new Visit();
        visit.setVisitNo(visitNo);
        visit.setPatientId(patient.getId());
        visit.setVisitType(isBlank(txt(pv1, "inpno")) ? 1 : 2);
        visit.setDepartment(first(txt(pv1, "dept_name"), txt(pv1, "inp_ward_name")));
        visit.setBedNo(txt(pv1, "inp_bed_no"));
        visit.setVisitTime(LocalDateTime.now());
        visit.setCreateTime(LocalDateTime.now());
        visitMapper.insert(visit);
        return visit;
    }

    /**
     * 收费明细按 order_id 建索引，取项目实际价格
     */
    private Map<String, BigDecimal> collectFees(JsonNode input) {
        Map<String, BigDecimal> feeMap = new HashMap<>();
        for (JsonNode fee : input.path("fee_item")) {
            String orderId = txt(fee, "order_id");
            if (orderId.isEmpty() || feeMap.containsKey(orderId)) {
                continue;
            }
            BigDecimal price = decimal(fee, "fee_price");
            if (price == null) {
                price = decimal(fee, "actual_money");
            }
            if (price != null) {
                feeMap.put(orderId, price);
            }
        }
        return feeMap;
    }

    /**
     * HIS 项目对应 LIS 检验项目: 先按编码、再按名称匹配; 都匹配不到时按配置自动建档
     */
    private Long resolveTestItem(String code, String name, String specimenType,
                                 boolean autoCreate, Map<String, Long> cache) {
        String cacheKey = code + "|" + name;
        Long cached = cache.get(cacheKey);
        if (cached != null) {
            return cached;
        }
        Long id = null;
        if (!code.isEmpty()) {
            List<Long> ids = jdbcTemplate.queryForList(
                    "SELECT id FROM lis_test_item WHERE item_code = ? LIMIT 1", Long.class, code);
            if (!ids.isEmpty()) {
                id = ids.get(0);
            }
        }
        if (id == null && !name.isEmpty()) {
            List<Long> ids = jdbcTemplate.queryForList(
                    "SELECT id FROM lis_test_item WHERE item_name = ? LIMIT 1", Long.class, name);
            if (!ids.isEmpty()) {
                id = ids.get(0);
            }
        }
        if (id != null) {
            cache.put(cacheKey, id);
            return id;
        }
        if (!autoCreate) {
            throw new IllegalArgumentException("检验项目未匹配: " + name + "(" + code + ")");
        }
        String newCode = code.isEmpty() ? "HIS" + System.currentTimeMillis() : code;
        List<Long> ids = jdbcTemplate.queryForList(
                "INSERT INTO lis_test_item (item_code, item_name, specimen_type, category, status) "
                        + "VALUES (?, ?, ?, ?, 1) ON CONFLICT (item_code) DO NOTHING RETURNING id",
                Long.class, newCode, name.isEmpty() ? newCode : name,
                specimenType.isEmpty() ? null : specimenType, "HIS导入");
        if (ids.isEmpty()) {
            ids = jdbcTemplate.queryForList(
                    "SELECT id FROM lis_test_item WHERE item_code = ?", Long.class, newCode);
        }
        if (ids.isEmpty()) {
            throw new IllegalArgumentException("检验项目自动建档失败: " + name + "(" + code + ")");
        }
        log.info("[HIS申请] 自动新增检验项目 code={}, name={}", newCode, name);
        cache.put(cacheKey, ids.get(0));
        return ids.get(0);
    }

    /**
     * 采集方式按名称匹配 lis_collection_method，匹配不到只保留名称
     */
    private Long findCollectionMethodId(String methodName) {
        if (isBlank(methodName)) {
            return null;
        }
        List<Long> ids = jdbcTemplate.queryForList(
                "SELECT id FROM lis_collection_method WHERE method_name = ? LIMIT 1", Long.class, methodName);
        return ids.isEmpty() ? null : ids.get(0);
    }

    private boolean existsHisApplyId(String hisApplyId) {
        if (isBlank(hisApplyId)) {
            return false;
        }
        return labOrderService.count(new LambdaQueryWrapper<LabOrder>()
                .eq(LabOrder::getHisApplyId, hisApplyId)) > 0;
    }

    /**
     * 读取系统参数，缺失时返回默认值
     */
    private String cfg(String key, String defaultValue) {
        try {
            List<String> values = jdbcTemplate.queryForList(
                    "SELECT config_value FROM sys_config WHERE config_key = ? LIMIT 1", String.class, key);
            if (values.isEmpty() || values.get(0) == null) {
                return defaultValue;
            }
            return values.get(0).trim();
        } catch (Exception e) {
            log.warn("[HIS申请] 读取参数 {} 失败: {}", key, e.getMessage());
            return defaultValue;
        }
    }

    private String resolveOrderType(JsonNode pv1, JsonNode apply) {
        if ("1".equals(txt(apply, "emg_sign"))) {
            return "急诊";
        }
        return isBlank(txt(pv1, "inpno")) ? "门诊" : "住院";
    }

    /**
     * 取附加信息(病人主诉/临床诊断/生理周期等)的 item_content
     */
    private String addition(JsonNode apply, String itemName) {
        for (JsonNode node : apply.path("addition_item")) {
            if (itemName.equals(txt(node, "item_name"))) {
                return txt(node, "item_content");
            }
        }
        return "";
    }

    private String dzContent(JsonNode input) {
        for (JsonNode node : input.path("dg1_info")) {
            String dz = txt(node, "dz_content");
            if (!dz.isEmpty()) {
                return dz;
            }
        }
        return "";
    }

    /**
     * 去掉诊断前的编码前缀, 如 "(061015)水痘、带状疱疹" -> "水痘、带状疱疹"
     */
    private String stripDiag(String value) {
        return value == null ? "" : value.replaceFirst("^\\([^)]*\\)", "").trim();
    }

    private String txt(JsonNode node, String field) {
        if (node == null || node.isMissingNode()) {
            return "";
        }
        JsonNode value = node.path(field);
        if (value.isMissingNode() || value.isNull()) {
            return "";
        }
        return value.asText("").trim();
    }

    private String first(String... values) {
        for (String value : values) {
            if (!isBlank(value)) {
                return value;
            }
        }
        return "";
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

    private Integer gender(String sex) {
        if ("男".equals(sex)) {
            return 1;
        }
        if ("女".equals(sex)) {
            return 2;
        }
        return 0;
    }

    private Integer parseAge(String age) {
        if (isBlank(age)) {
            return null;
        }
        String digits = age.replaceAll("[^0-9]", "");
        return digits.isEmpty() ? null : Integer.valueOf(digits);
    }

    private LocalDate parseDate(String date) {
        if (isBlank(date)) {
            return null;
        }
        try {
            return LocalDate.parse(date.trim().replace('/', '-').substring(0, 10));
        } catch (Exception e) {
            return null;
        }
    }

    private LocalDateTime parseTime(String time) {
        if (isBlank(time)) {
            return null;
        }
        String value = time.trim().replace('/', '-').replace('T', ' ');
        if (value.length() > 19) {
            value = value.substring(0, 19);
        }
        try {
            return LocalDateTime.parse(value, TIME_FMT);
        } catch (Exception e) {
            return null;
        }
    }

    private BigDecimal decimal(JsonNode node, String field) {
        String value = txt(node, field);
        if (value.isEmpty()) {
            return null;
        }
        try {
            return new BigDecimal(value);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /**
     * HIS 平台应答: {"output":{"ack_info":{"exe_status":"A|E","err_msg":"..."}}}
     */
    private Map<String, Object> ack(String status, String message) {
        Map<String, Object> ackInfo = new LinkedHashMap<>();
        ackInfo.put("exe_status", status);
        ackInfo.put("err_msg", message == null ? "" : message);
        Map<String, Object> output = new LinkedHashMap<>();
        output.put("ack_info", ackInfo);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("output", output);
        return result;
    }

    /**
     * 撤销接口应答(按 4014 文档出参): {"input":{"ack_info":{...},"apply_info":{"order_id":"..."}}}
     */
    private Map<String, Object> ackInput(String status, String message, Map<String, Object> applyInfo) {
        Map<String, Object> ackInfo = new LinkedHashMap<>();
        ackInfo.put("exe_status", status);
        ackInfo.put("err_msg", message == null ? "" : message);
        Map<String, Object> input = new LinkedHashMap<>();
        input.put("ack_info", ackInfo);
        if (applyInfo != null) {
            input.put("apply_info", applyInfo);
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("input", input);
        return result;
    }
}
