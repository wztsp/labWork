package com.lis.integration.message;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link HL7MessageBuilder#buildQBP_Q21(String, String)} 单元测试 —— B-7 患者查询请求报文。
 * <p>
 * QBP^Q21 = Get Person Demographics, 是向 HIS 查询患者主索引的标准消息。
 * 断言重点: 报文结构 (MSH/QPD/RCP 三段)、消息类型字段、
 * MSH-10 与 QPD-2 的消息 ID 一致性, 以及 visitId 的可选拼装语义。
 */
@DisplayName("B-7 HL7MessageBuilder.buildQBP_Q21 患者查询报文构建")
class HL7MessageBuilderQbpQ21Test {

    private final HL7MessageBuilder builder = new HL7MessageBuilder();

    /** 按 HL7 标准 \r 切段 */
    private static String[] segments(String msg) {
        return msg.split("\\r\\n|\\r|\\n");
    }

    private static String field(String segment, int index) {
        String[] f = segment.split("\\|", -1);
        return index < f.length ? f[index] : null;
    }

    @Test
    @DisplayName("报文结构: 恰好 MSH / QPD / RCP 三段, 且均以 \\r 结尾")
    void structure_threeSegments() {
        String msg = builder.buildQBP_Q21("P10086", "V20260815001");
        String[] segs = segments(msg);
        assertEquals(3, segs.length, "QBP^Q21 应为 MSH/QPD/RCP 三段, 实际: " + segs.length);
        assertTrue(segs[0].startsWith("MSH|"), "第 1 段应为 MSH");
        assertTrue(segs[1].startsWith("QPD|"), "第 2 段应为 QPD");
        assertTrue(segs[2].startsWith("RCP|"), "第 3 段应为 RCP");
        assertTrue(msg.endsWith("\r"), "报文应以段分隔符 \\r 结尾");
    }

    @Test
    @DisplayName("MSH 段: 编码字符、收发方与消息类型 QBP^Q21 正确")
    void mshSegment_fields() {
        String msg = builder.buildQBP_Q21("P10086", null);
        String msh = segments(msg)[0];
        assertEquals("^~\\&", field(msh, 1), "MSH-2 编码字符");
        assertEquals("LIS", field(msh, 2), "MSH-3 发送应用");
        assertEquals("LAB", field(msh, 3), "MSH-4 发送机构");
        assertEquals("HIS", field(msh, 4), "MSH-5 接收应用");
        assertEquals("HOSP", field(msh, 5), "MSH-6 接收机构");
        assertEquals("QBP^Q21", field(msh, 8), "MSH-9 消息类型必须是 QBP^Q21 (Get Person Demographics)");
        assertEquals("P", field(msh, 10), "MSH-11 处理 ID");
        assertEquals("2.5", field(msh, 11), "MSH-12 版本号");
    }

    @Test
    @DisplayName("MSH 段: MSH-7 时间戳为 14 位 yyyyMMddHHmmss")
    void mshSegment_timestampFormat() {
        String msh = segments(builder.buildQBP_Q21("P1", null))[0];
        String ts = field(msh, 6);
        assertEquals(14, ts.length(), "时间戳应为 14 位, 实际: " + ts);
        assertTrue(ts.matches("\\d{14}"), "时间戳应全为数字, 实际: " + ts);
    }

    @Test
    @DisplayName("QPD 段: 查询名称为 Q21^GET_PERSON_DEMO^HL7")
    void qpdSegment_queryName() {
        String qpd = segments(builder.buildQBP_Q21("P10086", null))[1];
        assertEquals("Q21^GET_PERSON_DEMO^HL7", field(qpd, 1), "QPD-1 查询名称");
    }

    @Test
    @DisplayName("QPD-3: 无 visitId 时只放 patientId")
    void qpdSegment_withoutVisitId() {
        assertEquals("P10086", field(segments(builder.buildQBP_Q21("P10086", null))[1], 3),
                "visitId 为 null 时 QPD-3 应只含 patientId");
        assertEquals("P10086", field(segments(builder.buildQBP_Q21("P10086", ""))[1], 3),
                "visitId 为空串时同样只含 patientId");
    }

    @Test
    @DisplayName("QPD-3: 有 visitId 时以 ^ 作为组件分隔拼接")
    void qpdSegment_withVisitId() {
        String qpd = segments(builder.buildQBP_Q21("P10086", "V20260815001"))[1];
        assertEquals("P10086^V20260815001", field(qpd, 3),
                "QPD-3 应为 patientId^visitId, 便于 HIS 精确匹配");
    }

    @Test
    @DisplayName("QPD-2 与 MSH-10 使用同一消息 ID, 便于 HIS 关联应答")
    void messageId_consistentBetweenMshAndQpd() {
        String[] segs = segments(builder.buildQBP_Q21("P10086", "V001"));
        String mshMsgId = field(segs[0], 9);
        String qpdMsgId = field(segs[1], 2);
        assertEquals(mshMsgId, qpdMsgId, "MSH-10 与 QPD-2 的消息 ID 必须一致");
        assertTrue(mshMsgId.startsWith("QBP"), "消息 ID 应以 QBP 前缀, 实际: " + mshMsgId);
    }

    @Test
    @DisplayName("消息 ID 单调递增, 连续两次构建不重复")
    void messageId_isUnique() {
        String id1 = field(segments(builder.buildQBP_Q21("P1", null))[0], 9);
        String id2 = field(segments(builder.buildQBP_Q21("P1", null))[0], 9);
        assertNotEquals(id1, id2, "同一 builder 连续构建的消息 ID 不得重复");
    }

    @Test
    @DisplayName("RCP 段: 响应控制参数为 I / ALL^ALL")
    void rcpSegment_fields() {
        String rcp = segments(builder.buildQBP_Q21("P10086", null))[2];
        assertEquals("I", field(rcp, 1), "RCP-1 查询优先级 I=Immediate");
        assertEquals("ALL^ALL", field(rcp, 2), "RCP-2 返回全部匹配项");
    }

    @Test
    @DisplayName("patientId 为 null 时不产生字面量 'null' (防御性)")
    void nullPatientId_doesNotLeakLiteralNull() {
        String msg = builder.buildQBP_Q21(null, null);
        assertFalse(msg.contains("null"), "报文中不得出现字面量 null, 实际: " + msg);
        assertEquals("", field(segments(msg)[1], 3), "QPD-3 应为空串");
    }

    @Test
    @DisplayName("患者 ID 原样透传, 不做截断或转义丢失")
    void patientId_passedThrough() {
        String qpd = segments(builder.buildQBP_Q21("ABC-123_456", null))[1];
        assertEquals("ABC-123_456", field(qpd, 3));
    }

    @Test
    @DisplayName("与 buildQBP_Q22 区分: 消息类型和查询名称都不同, 未复用错模板")
    void distinctFromQ22() {
        String q21 = builder.buildQBP_Q21("P1", null);
        String q22 = builder.buildQBP_Q22("20260801000000", "20260831235959");
        assertTrue(q21.contains("|QBP^Q21|"), "Q21 报文应含 QBP^Q21");
        assertFalse(q21.contains("|QBP^Q22|"), "Q21 报文不得含 QBP^Q22");
        assertTrue(q21.contains("Q21^GET_PERSON_DEMO^HL7"));
        assertTrue(q22.contains("Q22^LAB_ORDER_QUERY^HL7"));
        assertFalse(q21.contains("LAB_ORDER_QUERY"), "Q21 不得复用 Q22 的查询名称");
    }
}
