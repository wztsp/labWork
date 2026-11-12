package com.lis.integration.message;

import com.lis.api.dto.OrderDTO;
import com.lis.integration.dto.PatientInfo;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * B-7 <b>段分隔符潜伏 Bug 回归测试</b> —— 本轮最高价值用例。
 * <p>
 * <b>缺陷背景</b>: {@code HL7MessageParser} 原先用 {@code split("\r?\n")} 切分段,
 * 但 HL7 v2 / MLLP 的标准段分隔符是<b>纯 {@code \r} (CR)</b> ——
 * 且本工程自己的 {@code HL7MessageBuilder} 产出的报文也全部以 {@code \r} 结尾。
 * 这意味着现网一旦收到标准 {@code \r} 报文, {@code parseADT_A01} /
 * {@code parseORM_O01} / {@code parseRSP_K22} 会把<b>整条报文当成一个段</b>,
 * 只有开头的 {@code MSH} 能匹配, PID/PV1/OBR <b>全部解析不出来</b>, 且不抛异常
 * —— 静默返回空对象, 属于最难排查的一类故障。
 * <p>
 * 修复后统一走私有 {@code splitSegments}, 正则为 {@code \r\n|\r|\n} (兼容超集)。
 * 本测试对<b>三种分隔符各跑一遍</b>, 并断言三者解析结果<b>完全一致</b>。
 */
@DisplayName("B-7 HL7 段分隔符 (CR / CRLF / LF) 回归")
class HL7SegmentSeparatorTest {

    private final HL7MessageParser parser = new HL7MessageParser();

    /** HL7 v2 标准段分隔符: 纯 CR。修复前正是这种报文解析全废。 */
    private static final String CR = "\r";
    private static final String CRLF = "\r\n";
    private static final String LF = "\n";

    // ---------------------------------------------------------------------
    // 报文装配助手: 按字段下标精确构造, 避免手数竖线出错
    // ---------------------------------------------------------------------

    /** 构造 PID 段: 下标 3=患者ID 5=姓名 6=出生日期 7=性别 13=电话 19=地址 */
    private static String pid(String patientId, String name, String birth,
                              String gender, String phone, String address) {
        String[] f = new String[20];
        java.util.Arrays.fill(f, "");
        f[0] = "PID";
        f[3] = patientId;
        f[5] = name;
        f[6] = birth;
        f[7] = gender;
        f[13] = phone;
        f[19] = address;
        return String.join("|", f);
    }

    /** 构造 PV1 段: 下标 2=就诊类别 3=科室 4=床号 8=主治医生 */
    private static String pv1(String visitType, String dept, String bedNo, String doctor) {
        String[] f = new String[9];
        java.util.Arrays.fill(f, "");
        f[0] = "PV1";
        f[2] = visitType;
        f[3] = dept;
        f[4] = bedNo;
        f[8] = doctor;
        return String.join("|", f);
    }

    private static final String MSH_RSP =
            "MSH|^~\\&|HIS|HOSP|LIS|LAB|20260815103000||RSP^K21|MSGID001|P|2.5";
    private static final String MSA_OK = "MSA|AA|QBP001";

    /** 用指定分隔符拼装一条完整 RSP^K21 报文 */
    private static String rspK21(String sep) {
        return String.join(sep,
                MSH_RSP,
                MSA_OK,
                pid("P10086", "张三^", "19800101", "M", "13800000000", "北京市海淀区"),
                pv1("I", "CARDIOLOGY", "B-12", "李医生"));
    }

    private static void assertFullyParsed(PatientInfo info, String sepName) {
        assertNotNull(info, sepName + ": 解析结果不应为 null");
        assertEquals("P10086", info.getPatientId(), sepName + ": PID-3 患者ID 未解析出来");
        assertEquals("张三^", info.getPatientName(), sepName + ": PID-5 姓名未解析出来");
        assertEquals("M", info.getGender(), sepName + ": PID-7 性别未解析出来");
        assertEquals(LocalDate.of(1980, 1, 1), info.getBirthday(), sepName + ": PID-6 出生日期未解析出来");
        assertEquals("13800000000", info.getPhone(), sepName + ": PID-13 电话未解析出来");
        assertEquals("北京市海淀区", info.getAddress(), sepName + ": PID-19 地址未解析出来");
        assertEquals("I", info.getVisitType(), sepName + ": PV1-2 就诊类别未解析出来");
        assertEquals("CARDIOLOGY", info.getDepartment(), sepName + ": PV1-3 科室未解析出来");
        assertEquals("B-12", info.getBedNo(), sepName + ": PV1-4 床号未解析出来");
        assertEquals("李医生", info.getDoctorName(), sepName + ": PV1-9 医生未解析出来");
    }

    // =====================================================================
    // 核心: 三种分隔符各测一遍
    // =====================================================================

    @Test
    @DisplayName("parseRSP_K21: 纯 \\r 分隔 (HL7 标准) 必须完整解析 —— 修复前此用例必红")
    void parseRSP_K21_withPureCR() {
        assertFullyParsed(parser.parseRSP_K21(rspK21(CR)), "CR(\\r)");
    }

    @Test
    @DisplayName("parseRSP_K21: \\r\\n 分隔必须完整解析")
    void parseRSP_K21_withCRLF() {
        assertFullyParsed(parser.parseRSP_K21(rspK21(CRLF)), "CRLF(\\r\\n)");
    }

    @Test
    @DisplayName("parseRSP_K21: 纯 \\n 分隔必须完整解析")
    void parseRSP_K21_withPureLF() {
        assertFullyParsed(parser.parseRSP_K21(rspK21(LF)), "LF(\\n)");
    }

    @Test
    @DisplayName("parseRSP_K21: 三种分隔符解析结果必须完全等价")
    void parseRSP_K21_allSeparatorsEquivalent() {
        PatientInfo byCr = parser.parseRSP_K21(rspK21(CR));
        PatientInfo byCrlf = parser.parseRSP_K21(rspK21(CRLF));
        PatientInfo byLf = parser.parseRSP_K21(rspK21(LF));
        assertEquals(byCr, byCrlf, "\\r 与 \\r\\n 的解析结果必须一致");
        assertEquals(byCr, byLf, "\\r 与 \\n 的解析结果必须一致");
    }

    @Test
    @DisplayName("parseRSP_K21: 混合分隔符 (\\r\\n + \\r + \\n) 也能正确切段")
    void parseRSP_K21_mixedSeparators() {
        String msg = MSH_RSP + CRLF
                + MSA_OK + CR
                + pid("P10086", "张三^", "19800101", "M", "13800000000", "北京市海淀区") + LF
                + pv1("I", "CARDIOLOGY", "B-12", "李医生");
        assertFullyParsed(parser.parseRSP_K21(msg), "MIXED");
    }

    @Test
    @DisplayName("parseRSP_K21: 报文尾部带多余分隔符不产生错误段")
    void parseRSP_K21_trailingSeparator() {
        assertFullyParsed(parser.parseRSP_K21(rspK21(CR) + CR), "CR + 尾部分隔符");
        assertFullyParsed(parser.parseRSP_K21(rspK21(CRLF) + CRLF), "CRLF + 尾部分隔符");
    }

    // =====================================================================
    // splitSegments 是共享私有方法, 其余三个既有解析方法一并回归
    // =====================================================================

    @Test
    @DisplayName("parseADT_A01: 纯 \\r 分隔回归 (共享 splitSegments)")
    void parseADT_A01_withPureCR() {
        String msg = String.join(CR,
                "MSH|^~\\&|HIS|LAB|LIS|HOSP|20260815103000||ADT^A01|1|P|2.5",
                pid("1001", "Smith^John", "19800101", "M", "13800000000", "ADDR1"),
                pv1("O", "DEPT01", "BED01", "DOC1"));
        PatientInfo info = parser.parseADT_A01(msg);
        assertNotNull(info);
        assertEquals("1001", info.getPatientId(), "\\r 报文下 ADT^A01 的 PID 必须能解析");
        assertEquals("Smith^John", info.getPatientName());
        assertEquals("DEPT01", info.getDepartment(), "\\r 报文下 ADT^A01 的 PV1 必须能解析");
        assertEquals("BED01", info.getBedNo());
    }

    @Test
    @DisplayName("parseORM_O01: 纯 \\r 分隔回归 (共享 splitSegments)")
    void parseORM_O01_withPureCR() {
        String msg = String.join(CR,
                "MSH|^~\\&|HIS|LAB|LIS|HOSP|20260815103000||ORM^O01|1|P|2.5",
                "PID|||1001||Smith^John||19800101|M",
                "OBR|1|ORD123||^GLU^Glucose|20260815103000",
                "PV1||O|DEPT01");
        OrderDTO order = parser.parseORM_O01(msg);
        assertNotNull(order);
        assertEquals("ORD123", order.getOrderNo(), "\\r 报文下 OBR 必须能解析出申请号");
        assertEquals(1001L, order.getPatientId());
        assertEquals("DEPT01", order.getApplyDept());
        assertEquals(1, order.getItems().size());
        assertEquals("GLU", order.getItems().get(0).getTestItemCode());
    }

    @Test
    @DisplayName("parseRSP_K22: 纯 \\r 分隔回归, 多 OBR 分组正确切分")
    void parseRSP_K22_withPureCR() {
        String msg = String.join(CR,
                "MSH|^~\\&|HIS|LAB|LIS|HOSP|20260815103000||RSP^K22|1|P|2.5",
                "PID|||2001||Doe^Jane",
                "OBR|1|ORD001||^GLU^Glucose",
                "OBR|2|ORD002||^ALT^Alanine");
        List<OrderDTO> orders = parser.parseRSP_K22(msg);
        assertEquals(2, orders.size(), "\\r 报文下应切出 2 条申请");
        assertEquals("ORD001", orders.get(0).getOrderNo());
        assertEquals("ORD002", orders.get(1).getOrderNo());
        assertEquals(2001L, orders.get(0).getPatientId());
    }

    // =====================================================================
    // 端到端闭环: Builder 产出 \r 报文 → Parser 必须吃得下
    // =====================================================================

    @Test
    @DisplayName("闭环: HL7MessageBuilder 产出的 \\r 报文能被 splitSegments 正确切段")
    void builderOutputIsParsable() {
        HL7MessageBuilder builder = new HL7MessageBuilder();
        String qbp = builder.buildQBP_Q21("P10086", "V001");
        assertTrue(qbp.contains(CR), "Builder 产出必须使用 HL7 标准的 \\r 段分隔符");
        // 用与 parser 同一套正则切段, 验证能切出 MSH / QPD / RCP 三段
        String[] segs = qbp.split("\\r\\n|\\r|\\n");
        assertEquals(3, segs.length, "应切出 MSH/QPD/RCP 三段, 实际: " + segs.length);
        assertTrue(segs[0].startsWith("MSH|"));
        assertTrue(segs[1].startsWith("QPD|"));
        assertTrue(segs[2].startsWith("RCP|"));
    }

    // =====================================================================
    // parseRSP_K21 容错
    // =====================================================================

    @Test
    @DisplayName("parseRSP_K21: 空报文返回 null, 不抛异常")
    void parseRSP_K21_blank_returnsNull() {
        assertNull(parser.parseRSP_K21(null));
        assertNull(parser.parseRSP_K21(""));
        assertNull(parser.parseRSP_K21("   "));
        assertNull(parser.parseRSP_K21("\r\n"));
    }

    @Test
    @DisplayName("parseRSP_K21: 缺失 PID 与 PV1 段时返回空对象而非抛异常 (宽松容错)")
    void parseRSP_K21_noPidNoPv1_returnsEmptyObject() {
        PatientInfo info = parser.parseRSP_K21(MSH_RSP + CR + MSA_OK);
        assertNotNull(info, "只有 MSH/MSA 时应返回空对象, 由 adapter 决定降级");
        assertNull(info.getPatientId());
        assertNull(info.getPatientName());
        assertNull(info.getDepartment());
    }

    @Test
    @DisplayName("parseRSP_K21: 只有 PID 无 PV1 —— 患者字段有值, 就诊字段为 null")
    void parseRSP_K21_pidOnly() {
        String msg = MSH_RSP + CR + pid("P1", "N1", "19900202", "F", "139", "ADDR");
        PatientInfo info = parser.parseRSP_K21(msg);
        assertNotNull(info);
        assertEquals("P1", info.getPatientId());
        assertEquals("F", info.getGender());
        assertNull(info.getDepartment(), "无 PV1 时科室应为 null");
        assertNull(info.getBedNo());
        assertNull(info.getVisitType());
    }

    @Test
    @DisplayName("parseRSP_K21: 只有 PV1 无 PID —— 就诊字段有值, 患者字段为 null")
    void parseRSP_K21_pv1Only() {
        String msg = MSH_RSP + CR + pv1("O", "ER", "N/A", "王医生");
        PatientInfo info = parser.parseRSP_K21(msg);
        assertNotNull(info);
        assertNull(info.getPatientId(), "无 PID 时患者ID应为 null");
        assertEquals("O", info.getVisitType());
        assertEquals("ER", info.getDepartment());
        assertEquals("王医生", info.getDoctorName());
    }

    @Test
    @DisplayName("parseRSP_K21: PID 字段数不足时不得抛 IndexOutOfBounds")
    void parseRSP_K21_shortPid_noException() {
        PatientInfo info = parser.parseRSP_K21(MSH_RSP + CR + "PID|||P9");
        assertNotNull(info);
        assertEquals("P9", info.getPatientId());
        assertNull(info.getPatientName(), "字段缺失应留空");
        assertNull(info.getPhone());
        assertNull(info.getAddress());
    }

    @Test
    @DisplayName("parseRSP_K21: PV1 字段数不足时不得抛 IndexOutOfBounds")
    void parseRSP_K21_shortPv1_noException() {
        PatientInfo info = parser.parseRSP_K21(MSH_RSP + CR + "PV1|1|I");
        assertNotNull(info);
        assertEquals("I", info.getVisitType());
        assertNull(info.getDepartment());
        assertNull(info.getDoctorName());
    }

    @Test
    @DisplayName("parseRSP_K21: 出生日期非法时留空, 其余字段照常解析 (不因单字段炸掉整条)")
    void parseRSP_K21_invalidBirthday_tolerated() {
        String msg = MSH_RSP + CR + pid("P1", "N1", "NOTADATE", "M", "139", "ADDR");
        PatientInfo info = parser.parseRSP_K21(msg);
        assertNotNull(info);
        assertNull(info.getBirthday(), "非法出生日期应留空");
        assertEquals("P1", info.getPatientId(), "其余字段必须照常解析");
        assertEquals("M", info.getGender());
        assertEquals("ADDR", info.getAddress());
    }

    @Test
    @DisplayName("parseRSP_K21: 带时分秒的出生日期取前 8 位解析")
    void parseRSP_K21_birthdayWithTime() {
        String msg = MSH_RSP + CR + pid("P1", "N1", "19800101120000", "M", "139", "ADDR");
        PatientInfo info = parser.parseRSP_K21(msg);
        assertEquals(LocalDate.of(1980, 1, 1), info.getBirthday());
    }

    @Test
    @DisplayName("parseRSP_K21: 报文含空行时跳过, 不影响其余段")
    void parseRSP_K21_emptyLinesSkipped() {
        String msg = MSH_RSP + CR + CR
                + pid("P10086", "张三^", "19800101", "M", "13800000000", "北京市海淀区") + CR + CR
                + pv1("I", "CARDIOLOGY", "B-12", "李医生");
        assertFullyParsed(parser.parseRSP_K21(msg), "含空行");
    }
}
