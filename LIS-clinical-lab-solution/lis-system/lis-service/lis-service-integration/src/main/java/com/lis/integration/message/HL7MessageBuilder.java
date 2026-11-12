package com.lis.integration.message;

import com.lis.integration.dto.LabReport;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.format.DateTimeFormatter;
import java.util.concurrent.atomic.AtomicLong;

/**
 * HL7 消息构建器
 * <p>
 * 支持:
 * - ORU^R01: 结果回传
 * - ACK: 应答确认
 */
@Slf4j
@Component
public class HL7MessageBuilder {

    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");
    private final AtomicLong seq = new AtomicLong(0);

    /**
     * 构建 ORU^R01 结果回传消息
     */
    public String buildORU_R01(LabReport report) {
        StringBuilder sb = new StringBuilder();
        String now = java.time.LocalDateTime.now().format(FMT);
        String msgId = "ORU" + seq.incrementAndGet();

        // MSH 段
        sb.append("MSH|^~\\&|LIS|LAB|HIS|HOSP|").append(now).append("||ORU^R01|")
                .append(msgId).append("|P|2.5\r");
        // PID 段
        sb.append("PID|||").append(report.getPatientId()).append("||")
                .append(report.getPatientName()).append("||||||||||||||||\r");
        // OBR 段
        sb.append("OBR|1|").append(report.getSampleId()).append("||")
                .append("^").append(report.getReportType()).append("|")
                .append(report.getPublishTime() == null ? now : report.getPublishTime().format(FMT))
                .append("|||||||||||||||||||||||F\r");
        // OBX 段 (每项结果一条)
        if (report.getResults() != null) {
            int idx = 1;
            for (LabReport.ResultItem r : report.getResults()) {
                sb.append("OBX|").append(idx++).append("|NM|")
                        .append("^^^").append(r.getItemCode()).append("|")
                        .append(idx - 1).append("|")
                        .append(r.getResultValue()).append("|")
                        .append(r.getUnit() == null ? "" : r.getUnit()).append("|")
                        .append(r.getRefRange() == null ? "" : r.getRefRange()).append("|")
                        .append(r.getAbnormalFlag() == null ? "N" : r.getAbnormalFlag())
                        .append("|||||F\r");
            }
        }
        log.debug("构建 ORU^R01 消息: {}", msgId);
        return sb.toString();
    }

    /**
     * 构建 ACK 应答消息
     * @param originalMsgId 原始消息ID
     * @param code ACK码: AA=接受 AE=错误 AR=拒绝
     */
    public String buildACK(String originalMsgId, String code) {
        String now = java.time.LocalDateTime.now().format(FMT);
        return "MSH|^~\\&|LIS|LAB|HIS|HOSP|" + now + "||ACK|" + originalMsgId + "|P|2.5\r"
                + "MSA|" + code + "|" + originalMsgId + "\r";
    }

    /**
     * 构建 QBP^Q22 检验申请查询消息 (查询指定时间区间内的新申请)
     * @param startTime 查询起始时间
     * @param endTime 查询截止时间
     */
    public String buildQBP_Q22(String startTime, String endTime) {
        String now = java.time.LocalDateTime.now().format(FMT);
        String msgId = "QBP" + seq.incrementAndGet();
        StringBuilder sb = new StringBuilder();
        sb.append("MSH|^~\\&|LIS|LAB|HIS|HOSP|").append(now)
                .append("||QBP^Q22|").append(msgId).append("|P|2.5\r");
        // QPD: 查询参数
        sb.append("QPD|Q22^LAB_ORDER_QUERY^HL7|")
                .append(msgId).append("|")
                .append(startTime).append("^").append(endTime).append("\r");
        // RCP: 响应控制参数 (返回所有匹配项)
        sb.append("RCP|I|ALL^ALL|||||\r");
        log.debug("构建 QBP^Q22 消息: {}", msgId);
        return sb.toString();
    }

    /**
     * 构建 QBP^Q21 患者主索引查询消息 (Get Person Demographics)
     * <p>
     * 用于向 HIS 查询患者基本信息, 响应由 {@code HL7MessageParser#parseRSP_K21} 解析。
     *
     * @param patientId 患者ID (PID-3), 非空
     * @param visitId   就诊号 (可选, 作为查询附加参数回填)
     */
    public String buildQBP_Q21(String patientId, String visitId) {
        String now = java.time.LocalDateTime.now().format(FMT);
        String msgId = "QBP" + seq.incrementAndGet();
        StringBuilder sb = new StringBuilder();
        sb.append("MSH|^~\\&|LIS|LAB|HIS|HOSP|").append(now)
                .append("||QBP^Q21|").append(msgId).append("|P|2.5\r");
        // QPD: 查询参数 (患者主索引查询)
        sb.append("QPD|Q21^GET_PERSON_DEMO^HL7|").append(msgId).append("|");
        String qpd3 = (patientId == null ? "" : patientId);
        if (visitId != null && !visitId.isEmpty()) {
            // 就诊号作为 QPD-3 附加组件, 便于 HIS 精确匹配
            qpd3 = qpd3 + "^" + visitId;
        }
        sb.append(qpd3).append("\r");
        // RCP: 响应控制参数 (返回所有匹配项)
        sb.append("RCP|I|ALL^ALL|||||\r");
        log.debug("构建 QBP^Q21 消息: {}", msgId);
        return sb.toString();
    }

    /**
     * 构建简单的 HL7 ping 消息 (用于健康检查)
     */
    public String buildPing() {
        String now = java.time.LocalDateTime.now().format(FMT);
        String msgId = "PING" + seq.incrementAndGet();
        return "MSH|^~\\&|LIS|LAB|HIS|HOSP|" + now + "||QBP^ZPI|" + msgId + "|P|2.5\r"
                + "QPD|ZPI^PING^HL7|" + msgId + "|\r"
                + "RCP|I||||||\r";
    }
}
