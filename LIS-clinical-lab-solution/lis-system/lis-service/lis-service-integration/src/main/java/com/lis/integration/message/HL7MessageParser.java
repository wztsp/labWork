package com.lis.integration.message;

import com.lis.api.dto.OrderDTO;
import com.lis.api.dto.OrderItemDTO;
import com.lis.integration.dto.PatientInfo;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/**
 * HL7 消息解析器
 * <p>
 * 支持:
 * - ORM^O01: 检验申请接收
 * - ADT^A01: 患者入院/登记信息
 */
@Slf4j
@Component
public class HL7MessageParser {

    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("yyyyMMdd");

    /**
     * 解析 ORM^O01 检验申请消息
     */
    public OrderDTO parseORM_O01(String hl7Message) {
        if (hl7Message == null || hl7Message.isBlank()) {
            return null;
        }
        String[] segments = splitSegments(hl7Message);
        OrderDTO order = new OrderDTO();
        List<OrderItemDTO> items = new ArrayList<>();
        String patientId = null;
        String patientName = null;
        for (String seg : segments) {
            if (seg == null || seg.isEmpty()) continue;
            if (seg.startsWith("PID|")) {
                String[] fields = seg.split("\\|", -1);
                if (fields.length > 3) patientId = fields[3];
                if (fields.length > 5) patientName = fields[5];
            } else if (seg.startsWith("OBR|")) {
                String[] fields = seg.split("\\|", -1);
                // OBR|1|orderNo||^itemCode^itemName|...
                if (fields.length > 4) {
                    order.setOrderNo(fields[2]);
                    String itemField = fields[4];
                    String[] parts = itemField.split("\\^", -1);
                    OrderItemDTO item = new OrderItemDTO();
                    if (parts.length > 1) item.setTestItemCode(parts[1]);
                    if (parts.length > 2) item.setTestItemName(parts[2]);
                    items.add(item);
                }
            } else if (seg.startsWith("PV1|")) {
                String[] fields = seg.split("\\|", -1);
                // PV1||O|department|...  PV1.3 = 患者位置 (含科室)
                if (fields.length > 3) {
                    order.setApplyDept(fields[3]);
                }
            }
        }
        order.setPatientId(patientId == null ? null : tryParseLong(patientId));
        order.setPatientName(patientName);
        order.setItems(items);
        log.debug("解析 ORM^O01: orderNo={}, items={}", order.getOrderNo(), items.size());
        return order;
    }

    /**
     * 解析 ADT^A01 患者入院/登记消息
     */
    public PatientInfo parseADT_A01(String hl7Message) {
        if (hl7Message == null || hl7Message.isBlank()) {
            return null;
        }
        String[] segments = splitSegments(hl7Message);
        PatientInfo info = new PatientInfo();
        for (String seg : segments) {
            if (seg == null || seg.isEmpty()) continue;
            if (seg.startsWith("PID|")) {
                String[] fields = seg.split("\\|", -1);
                // PID|||patientId||name|birthday|gender|...
                if (fields.length > 3) info.setPatientId(fields[3]);
                if (fields.length > 5) info.setPatientName(fields[5]);
                if (fields.length > 7) info.setGender(fields[7]);
                if (fields.length > 6 && !fields[6].isEmpty()) {
                    try {
                        info.setBirthday(LocalDate.parse(fields[6].substring(0, 8), DATE_FMT));
                    } catch (Exception ignore) {}
                }
                if (fields.length > 13) info.setPhone(fields[13]);
                if (fields.length > 19) info.setAddress(fields[19]);
            } else if (seg.startsWith("PV1|")) {
                String[] fields = seg.split("\\|", -1);
                // PV1.2 = 患者类别 (I=住院 O=门诊 E=急诊), PV1.3 = 位置 (含科室), PV1.4 = 床号
                if (fields.length > 2) info.setVisitType(fields[2]);
                if (fields.length > 3) info.setDepartment(fields[3]);
                if (fields.length > 4) info.setBedNo(fields[4]);
                if (fields.length > 8) info.setDoctorName(fields[8]);
            }
        }
        log.debug("解析 ADT^A01: patientId={}, name={}", info.getPatientId(), info.getPatientName());
        return info;
    }

    /**
     * 解析 RSP^K22 检验申请查询响应消息
     * <p>
     * 一条 RSP^K22 通常包含多个 PID/OBR 分组，每组对应一个患者的一个申请。
     * 本方法将响应解析为多个 {@link OrderDTO}。
     */
    public List<OrderDTO> parseRSP_K22(String hl7Message) {
        if (hl7Message == null || hl7Message.isBlank()) {
            return java.util.Collections.emptyList();
        }
        String[] segments = splitSegments(hl7Message);
        List<OrderDTO> orders = new ArrayList<>();
        OrderDTO current = null;
        String patientId = null;
        String patientName = null;
        for (String seg : segments) {
            if (seg == null || seg.isEmpty()) continue;
            if (seg.startsWith("PID|")) {
                String[] fields = seg.split("\\|", -1);
                if (fields.length > 3) patientId = fields[3];
                if (fields.length > 5) patientName = fields[5];
            } else if (seg.startsWith("OBR|")) {
                // 遇到新的 OBR, 视为一条新的申请
                if (current != null) {
                    orders.add(current);
                }
                current = new OrderDTO();
                List<OrderItemDTO> items = new ArrayList<>();
                String[] fields = seg.split("\\|", -1);
                if (fields.length > 2) current.setOrderNo(fields[2]);
                if (fields.length > 4) {
                    String itemField = fields[4];
                    String[] parts = itemField.split("\\^", -1);
                    OrderItemDTO item = new OrderItemDTO();
                    if (parts.length > 1) item.setTestItemCode(parts[1]);
                    if (parts.length > 2) item.setTestItemName(parts[2]);
                    items.add(item);
                }
                current.setItems(items);
                current.setPatientId(patientId == null ? null : tryParseLong(patientId));
                current.setPatientName(patientName);
            } else if (seg.startsWith("PV1|") && current != null) {
                String[] fields = seg.split("\\|", -1);
                if (fields.length > 3) current.setApplyDept(fields[3]);
            }
        }
        if (current != null) {
            orders.add(current);
        }
        log.debug("解析 RSP^K22: 申请数={}", orders.size());
        return orders;
    }

    /**
     * 将 HL7 报文切分为段数组 (B-7 潜伏 Bug 修复)。
     * <p>
     * HL7/MLLP 段分隔符是<b>纯 {@code \r}</b>, 且 {@code sendAndReceive} 回填也用 {@code \r}。
     * 原 {@code split("\r?\n")} 在纯 {@code \r} 报文下无法切分, 导致 PID/PV1 全部解析失败。
     * 本方法用 {@code \r\n|\r|\n} 覆盖三种换行, 是兼容超集。
     *
     * @param hl7Message HL7 报文 (可含 {@code \r} / {@code \n} / {@code \r\n} 混合分隔)
     * @return 段数组 (空报文返回空数组)
     */
    private String[] splitSegments(String hl7Message) {
        if (hl7Message == null) {
            return new String[0];
        }
        return hl7Message.split("\\r\\n|\\r|\\n");
    }

    /**
     * 解析 RSP^K21 患者主索引查询响应消息 (B-7 患者查询)。
     * <p>
     * 从响应中的 PID / PV1 段提取患者基本信息到 {@link PatientInfo}。
     * 解析采用宽松容错: 缺失段或字段留空, 异常由调用方 (adapter) 兜底返回 null。
     *
     * @param hl7Message RSP^K21 响应报文 (已去除 MLLP 包装)
     * @return 解析出的患者信息; 报文为空返回 null
     */
    public PatientInfo parseRSP_K21(String hl7Message) {
        if (hl7Message == null || hl7Message.isBlank()) {
            return null;
        }
        String[] segments = splitSegments(hl7Message);
        PatientInfo info = new PatientInfo();
        for (String seg : segments) {
            if (seg == null || seg.isEmpty()) continue;
            if (seg.startsWith("PID|")) {
                String[] fields = seg.split("\\|", -1);
                // PID|||patientId||name|birthday|gender|...|phone|...|...|...|address
                if (fields.length > 3) info.setPatientId(fields[3]);
                if (fields.length > 5) info.setPatientName(fields[5]);
                if (fields.length > 7) info.setGender(fields[7]);
                if (fields.length > 6 && !fields[6].isEmpty()) {
                    try {
                        info.setBirthday(LocalDate.parse(fields[6].substring(0, 8), DATE_FMT));
                    } catch (Exception ignore) {
                        // 日期格式异常留空, 不阻断整体解析
                    }
                }
                if (fields.length > 13) info.setPhone(fields[13]);
                if (fields.length > 19) info.setAddress(fields[19]);
            } else if (seg.startsWith("PV1|")) {
                String[] fields = seg.split("\\|", -1);
                // PV1.2 = 就诊类别(I/O/E), PV1.3 = 科室, PV1.4 = 床号, PV1.9 = 主治医生
                if (fields.length > 2) info.setVisitType(fields[2]);
                if (fields.length > 3) info.setDepartment(fields[3]);
                if (fields.length > 4) info.setBedNo(fields[4]);
                if (fields.length > 8) info.setDoctorName(fields[8]);
            }
        }
        log.debug("解析 RSP^K21: patientId={}, name={}", info.getPatientId(), info.getPatientName());
        return info;
    }

    private Long tryParseLong(String s) {
        try {
            return Long.parseLong(s);
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
