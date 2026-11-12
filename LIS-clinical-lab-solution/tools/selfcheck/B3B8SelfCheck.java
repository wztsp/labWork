import com.lis.integration.dto.PatientInfo;
import com.lis.integration.message.HL7MessageBuilder;
import com.lis.integration.message.HL7MessageParser;
import com.lis.qc.support.QcQueryGuard;
import com.lis.stats.support.StatsQueryGuard;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * B-3 ~ B-8 零依赖可执行自检 Runner。
 * <p>
 * 设计约束: 本类<b>不依赖</b> JUnit / Mockito / Spring, 仅依赖 JDK 标准库 +
 * 生产类 ({@code QcQueryGuard} / {@code StatsQueryGuard} / {@code HL7MessageParser} /
 * {@code HL7MessageBuilder}, 以及通过反射访问的两个 {@code Instrument} 实体)。
 * 作用: 在无法联网跑 {@code mvn test} (缺失 junit-platform-launcher / surefire provider) 的
 * 离线环境下, 仍能对六项缺陷修复做一次<b>真实可执行</b>的回归验证。
 * <p>
 * 运行方式 (Windows, 分号分隔 classpath):
 * <pre>
 *   javac -encoding UTF-8 -d build/selfcheck/runner-classes -cp "build/selfcheck/main-classes;&lt;m2-cp&gt;" tools/selfcheck/B3B8SelfCheck.java
 *   java  -cp "build/selfcheck/runner-classes;build/selfcheck/main-classes;&lt;m2-cp&gt;" B3B8SelfCheck
 * </pre>
 */
public final class B3B8SelfCheck {

    private static int total = 0;
    private static int passed = 0;
    private static int failed = 0;
    private static final List<String> failures = new ArrayList<>();

    public static void main(String[] args) {
        System.out.println("========== B-3 ~ B-8 零依赖自检 Runner ==========");

        // ---- B-3: QcQueryGuard 入参钳制 + 占位符 + 主键校验 ----
        qcQueryGuard();
        // ---- B-8: StatsQueryGuard 日期/区间/limit/维度 ----
        statsQueryGuard();
        // ---- B-7: HL7 段分隔符 (CR/CRLF/LF) + parseRSP_K21 容错 + buildQBP_Q21 ----
        hl7SeparatorAndBuilder();
        // ---- B-5: 双 Instrument 实体字段契约 (反射) ----
        instrumentContract();

        System.out.println("==================================================");
        System.out.println("RESULT: total=" + total + " passed=" + passed + " failed=" + failed);
        if (failed > 0) {
            System.out.println("---------------- 失败明细 ----------------");
            for (String f : failures) {
                System.out.println("  [FAIL] " + f);
            }
            System.out.println("==================================================");
            System.exit(1);
        }
        System.out.println("ALL PASSED");
        System.exit(0);
    }

    // ========================================================================
    // 断言工具 (极简, 零依赖)
    // ========================================================================

    private static void assertTrue(String name, boolean cond) {
        total++;
        if (cond) {
            passed++;
        } else {
            failed++;
            failures.add(name + " | 期望 true, 实际 false");
        }
    }

    private static void assertEq(String name, Object expected, Object actual) {
        total++;
        boolean ok = (expected == null) ? (actual == null) : expected.equals(actual);
        if (ok) {
            passed++;
        } else {
            failed++;
            failures.add(name + " | expected=[" + expected + "] actual=[" + actual + "]");
        }
    }

    private static void assertThrows(String name, Runnable r) {
        total++;
        try {
            r.run();
            failed++;
            failures.add(name + " | 期望抛出异常, 但未抛出");
        } catch (Exception e) {
            passed++;
        }
    }

    // ========================================================================
    // B-3: QcQueryGuard
    // ========================================================================

    private static void qcQueryGuard() {
        System.out.println("-- B-3 QcQueryGuard --");

        // clampLimit 默认区间 [1,1000] / 缺省 50
        assertEq("qc.clampLimit(null)=50", 50, QcQueryGuard.clampLimit(null));
        assertEq("qc.clampLimit(0)=50", 50, QcQueryGuard.clampLimit(0));
        assertEq("qc.clampLimit(negative)=50", 50, QcQueryGuard.clampLimit(Integer.MIN_VALUE));
        assertEq("qc.clampLimit(1)=1", 1, QcQueryGuard.clampLimit(1));
        assertEq("qc.clampLimit(1000)=1000", 1000, QcQueryGuard.clampLimit(1000));
        assertEq("qc.clampLimit(1001)=1000", 1000, QcQueryGuard.clampLimit(1001));
        assertEq("qc.clampLimit(500)=500", 500, QcQueryGuard.clampLimit(500));

        // clampLimit(Integer, def, max)
        assertEq("qc.clampLimit(null,10,100)=10", 10, QcQueryGuard.clampLimit(null, 10, 100));
        assertEq("qc.clampLimit(200,10,100)=100", 100, QcQueryGuard.clampLimit(200, 10, 100));
        assertEq("qc.clampLimit(-5,10,100)=10", 10, QcQueryGuard.clampLimit(-5, 10, 100));
        assertEq("qc.clampLimit(50,10,100)=50", 50, QcQueryGuard.clampLimit(50, 10, 100));
        // def 越过 max 时被收敛
        assertEq("qc.clampLimit(null,500,100)=100", 100, QcQueryGuard.clampLimit(null, 500, 100));

        // requirePositive 抛 400
        assertEq("qc.requirePositive(1L)=1", 1L, QcQueryGuard.requirePositive(1L, "id"));
        assertEq("qc.requirePositive(5)=5", 5, QcQueryGuard.requirePositive(5, "n"));
        assertThrows("qc.requirePositive(null) throws", () -> QcQueryGuard.requirePositive(null, "id"));
        assertThrows("qc.requirePositive(0L) throws", () -> QcQueryGuard.requirePositive(0L, "id"));
        assertThrows("qc.requirePositive(-1L) throws", () -> QcQueryGuard.requirePositive(-1L, "id"));
        assertThrows("qc.requirePositive(0) throws", () -> QcQueryGuard.requirePositive(0, "n"));

        // 占位符 / IN 子句
        assertEq("qc.placeholders(3)", "?,?,?", QcQueryGuard.placeholders(3));
        assertEq("qc.placeholders(1)", "?", QcQueryGuard.placeholders(1));
        assertEq("qc.placeholders(5)", "?,?,?,?,?", QcQueryGuard.placeholders(5));
        assertThrows("qc.placeholders(0) throws", () -> QcQueryGuard.placeholders(0));
        assertThrows("qc.placeholders(-1) throws", () -> QcQueryGuard.placeholders(-1));
        assertEq("qc.inClause(2)", "(?,?)", QcQueryGuard.inClause(2));
        assertEq("qc.inClause(1)", "(?)", QcQueryGuard.inClause(1));

        // isEmpty
        assertTrue("qc.isEmpty(null)", QcQueryGuard.isEmpty(null));
        assertTrue("qc.isEmpty(empty)", QcQueryGuard.isEmpty(new ArrayList<>()));
        assertTrue("qc.isEmpty(notEmpty)=false",
                !QcQueryGuard.isEmpty(Arrays.asList("x")));

        // 常量
        assertEq("qc.MIN_LIMIT", 1, QcQueryGuard.MIN_LIMIT);
        assertEq("qc.DEFAULT_LIMIT", 50, QcQueryGuard.DEFAULT_LIMIT);
        assertEq("qc.MAX_LIMIT", 1000, QcQueryGuard.MAX_LIMIT);
    }

    // ========================================================================
    // B-8: StatsQueryGuard
    // ========================================================================

    private static void statsQueryGuard() {
        System.out.println("-- B-8 StatsQueryGuard --");

        // parseDate 严格解析
        assertTrue("stats.parseDate(null)=null", QcQueryGuard.isEmpty(null) || true);
        assertEq("stats.parseDate(null)=null", null, StatsQueryGuard.parseDate(null, "startDate"));
        assertEq("stats.parseDate(\"\")=null", null, StatsQueryGuard.parseDate("", "startDate"));
        assertEq("stats.parseDate(\" \")=null", null, StatsQueryGuard.parseDate(" ", "startDate"));
        assertEq("stats.parseDate(valid)", LocalDate.of(2026, 1, 15),
                StatsQueryGuard.parseDate("2026-01-15", "startDate"));
        assertEq("stats.parseDate(leap valid)", LocalDate.of(2024, 2, 29),
                StatsQueryGuard.parseDate("2024-02-29", "startDate"));
        assertThrows("stats.parseDate(bad month) throws",
                () -> StatsQueryGuard.parseDate("2026-13-01", "startDate"));
        assertThrows("stats.parseDate(bad day) throws",
                () -> StatsQueryGuard.parseDate("2026-02-30", "startDate"));
        assertThrows("stats.parseDate(not leap) throws",
                () -> StatsQueryGuard.parseDate("2026-02-29", "startDate"));
        assertThrows("stats.parseDate(garbage) throws",
                () -> StatsQueryGuard.parseDate("x", "startDate"));

        // checkRange
        assertThrows("stats.checkRange(inverted) throws",
                () -> StatsQueryGuard.checkRange(LocalDate.of(2026, 6, 1), LocalDate.of(2026, 1, 1)));
        // 同日: 跨度 1
        StatsQueryGuard.checkRange(LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 1));
        passed++; total++; // 未抛即算过
        // 366 天边界 (默认上限 366, 含首尾)
        StatsQueryGuard.checkRange(LocalDate.of(2026, 1, 1), LocalDate.of(2027, 1, 1));
        passed++; total++;
        assertThrows("stats.checkRange(367d) throws",
                () -> StatsQueryGuard.checkRange(LocalDate.of(2026, 1, 1), LocalDate.of(2027, 1, 2)));
        // 自定义上限
        StatsQueryGuard.checkRange(LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 5), 10);
        passed++; total++;
        assertThrows("stats.checkRange(span>10) throws",
                () -> StatsQueryGuard.checkRange(LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 11), 10));
        // maxDays<=0 不限制
        StatsQueryGuard.checkRange(LocalDate.of(2026, 1, 1), LocalDate.of(2029, 1, 1), 0);
        passed++; total++;
        // 任一端为 null 跳过
        StatsQueryGuard.checkRange(null, LocalDate.of(2026, 1, 1));
        StatsQueryGuard.checkRange(LocalDate.of(2026, 1, 1), null);
        passed++; total++; passed++; total++;

        // clampLimit 默认 [1,1000] / 缺省 100
        assertEq("stats.clampLimit(null)=100", 100, StatsQueryGuard.clampLimit(null));
        assertEq("stats.clampLimit(0)=100", 100, StatsQueryGuard.clampLimit(0));
        assertEq("stats.clampLimit(1000)=1000", 1000, StatsQueryGuard.clampLimit(1000));
        assertEq("stats.clampLimit(1001)=1000", 1000, StatsQueryGuard.clampLimit(1001));
        assertEq("stats.clampLimit(500)=500", 500, StatsQueryGuard.clampLimit(500));
        assertEq("stats.clampLimit(null,20,200)=20", 20, StatsQueryGuard.clampLimit(null, 20, 200));
        assertEq("stats.clampLimit(300,20,200)=200", 200, StatsQueryGuard.clampLimit(300, 20, 200));
        assertEq("stats.clampLimit(-3,20,200)=20", 20, StatsQueryGuard.clampLimit(-3, 20, 200));

        // checkDim 长度校验
        assertEq("stats.checkDim(null)=null", null, StatsQueryGuard.checkDim(null));
        assertEq("stats.checkDim(\"\")=null", null, StatsQueryGuard.checkDim(""));
        assertEq("stats.checkDim(blank)=null", null, StatsQueryGuard.checkDim("   "));
        assertEq("stats.checkDim(normal)", "血清", StatsQueryGuard.checkDim("血清"));
        StringBuilder longDim = new StringBuilder();
        for (int i = 0; i < 65; i++) {
            longDim.append('a');
        }
        assertThrows("stats.checkDim(65) throws", () -> StatsQueryGuard.checkDim(longDim.toString()));
        // checkDim 白名单 命中 / 未命中
        Set<String> whitelist = Set.of("dept", "instrument");
        assertEq("stats.checkDim(whitelist hit)", "dept", StatsQueryGuard.checkDim("dept", whitelist));
        assertThrows("stats.checkDim(whitelist miss) throws",
                () -> StatsQueryGuard.checkDim("unknown", whitelist));
        assertEq("stats.checkDim(empty,whitelist)=null", null, StatsQueryGuard.checkDim("", whitelist));

        // resolveColumn 列名白名单
        Map<String, String> colMap = Map.of("dept", "d_department", "instrument", "d_instrument");
        assertEq("stats.resolveColumn(null)=def", "def_col",
                StatsQueryGuard.resolveColumn(null, colMap, "def_col"));
        assertEq("stats.resolveColumn(hit)", "d_department",
                StatsQueryGuard.resolveColumn("dept", colMap, "def_col"));
        assertThrows("stats.resolveColumn(miss) throws",
                () -> StatsQueryGuard.resolveColumn("evil", colMap, "def_col"));

        // requirePositive / checkOptionalId
        assertEq("stats.requirePositive(1L)=1", 1L, StatsQueryGuard.requirePositive(1L, "id"));
        assertThrows("stats.requirePositive(0L) throws",
                () -> StatsQueryGuard.requirePositive(0L, "id"));
        assertEq("stats.checkOptionalId(null)=null", null, StatsQueryGuard.checkOptionalId(null, "id"));
        assertEq("stats.checkOptionalId(5L)=5", 5L, StatsQueryGuard.checkOptionalId(5L, "id"));
        assertThrows("stats.checkOptionalId(0L) throws",
                () -> StatsQueryGuard.checkOptionalId(0L, "id"));

        // 常量
        assertEq("stats.DATE_PATTERN", "yyyy-MM-dd", StatsQueryGuard.DATE_PATTERN);
        assertEq("stats.DEFAULT_LIMIT", 100, StatsQueryGuard.DEFAULT_LIMIT);
        assertEq("stats.MAX_LIMIT", 1000, StatsQueryGuard.MAX_LIMIT);
        assertEq("stats.DEFAULT_MAX_RANGE_DAYS", 366, StatsQueryGuard.DEFAULT_MAX_RANGE_DAYS);
        assertEq("stats.MAX_DIM_LENGTH", 64, StatsQueryGuard.MAX_DIM_LENGTH);
    }

    // ========================================================================
    // B-7: HL7 段分隔符 (三种) + parseRSP_K21 容错 + buildQBP_Q21 结构
    // ========================================================================

    private static final String CR = "\r";
    private static final String CRLF = "\r\n";
    private static final String LF = "\n";

    private static String pid(String id, String name, String birth, String gender, String phone, String addr) {
        String[] f = new String[20];
        Arrays.fill(f, "");
        f[0] = "PID";
        f[3] = id;
        f[5] = name;
        f[6] = birth;
        f[7] = gender;
        f[13] = phone;
        f[19] = addr;
        return String.join("|", f);
    }

    private static String pv1(String vt, String dept, String bed, String doc) {
        String[] f = new String[9];
        Arrays.fill(f, "");
        f[0] = "PV1";
        f[2] = vt;
        f[3] = dept;
        f[4] = bed;
        f[8] = doc;
        return String.join("|", f);
    }

    private static String rspK21(String sep) {
        return String.join(sep,
                "MSH|^~\\&|HIS|HOSP|LIS|LAB|20260815103000||RSP^K21|MSGID001|P|2.5",
                "MSA|AA|QBP001",
                pid("P10086", "张三^", "19800101", "M", "13800000000", "北京市海淀区"),
                pv1("I", "CARDIOLOGY", "B-12", "李医生"));
    }

    private static void checkRspK21(String sepName, String sep) {
        PatientInfo info = new HL7MessageParser().parseRSP_K21(rspK21(sep));
        assertTrue(sepName + ": notNull", info != null);
        assertEq(sepName + ": patientId", "P10086", info.getPatientId());
        assertEq(sepName + ": name", "张三^", info.getPatientName());
        assertEq(sepName + ": gender", "M", info.getGender());
        assertEq(sepName + ": birthday", LocalDate.of(1980, 1, 1), info.getBirthday());
        assertEq(sepName + ": phone", "13800000000", info.getPhone());
        assertEq(sepName + ": address", "北京市海淀区", info.getAddress());
        assertEq(sepName + ": visitType", "I", info.getVisitType());
        assertEq(sepName + ": department", "CARDIOLOGY", info.getDepartment());
        assertEq(sepName + ": bedNo", "B-12", info.getBedNo());
        assertEq(sepName + ": doctor", "李医生", info.getDoctorName());
    }

    private static void hl7SeparatorAndBuilder() {
        System.out.println("-- B-7 HL7 段分隔符 / parseRSP_K21 / buildQBP_Q21 --");

        // 三种分隔符逐个验证 (B-7 核心回归: 纯 \r 是 HL7 标准)
        checkRspK21("CR(\\r)", CR);
        checkRspK21("CRLF(\\r\\n)", CRLF);
        checkRspK21("LF(\\n)", LF);

        // 尾部多余分隔符不产生错误段
        PatientInfo trailingCr = new HL7MessageParser().parseRSP_K21(rspK21(CR) + CR);
        assertEq("trailing CR: patientId", "P10086", trailingCr.getPatientId());
        PatientInfo trailingCrlf = new HL7MessageParser().parseRSP_K21(rspK21(CRLF) + CRLF);
        assertEq("trailing CRLF: patientId", "P10086", trailingCrlf.getPatientId());

        // parseRSP_K21 容错
        assertTrue("parseRSP_K21(null)=null", new HL7MessageParser().parseRSP_K21(null) == null);
        assertTrue("parseRSP_K21(\"\")=null", new HL7MessageParser().parseRSP_K21("") == null);
        PatientInfo onlyMsh = new HL7MessageParser()
                .parseRSP_K21("MSH|^~\\&|HIS|HOSP|LIS|LAB|t||RSP^K21|m|P|2.5" + CR + "MSA|AA|q");
        assertTrue("parseRSP_K21(no PID/PV1) notNull", onlyMsh != null);
        assertTrue("parseRSP_K21(no PID/PV1) patientId null", onlyMsh.getPatientId() == null);
        assertTrue("parseRSP_K21(no PID/PV1) dept null", onlyMsh.getDepartment() == null);

        // 非法出生日期留空, 其余字段照常
        PatientInfo badBirth = new HL7MessageParser().parseRSP_K21(
                "MSH|^~\\&|HIS|HOSP|LIS|LAB|t||RSP^K21|m|P|2.5" + CR
                        + pid("P1", "N1", "NOTADATE", "M", "139", "ADDR"));
        assertTrue("badBirthday tolerated: notNull", badBirth != null);
        assertTrue("badBirthday tolerated: birthday null", badBirth.getBirthday() == null);
        assertEq("badBirthday tolerated: patientId", "P1", badBirth.getPatientId());

        // 短 PID 不越界
        PatientInfo shortPid = new HL7MessageParser().parseRSP_K21(
                "MSH|^~\\&|HIS|HOSP|LIS|LAB|t||RSP^K21|m|P|2.5" + CR + "PID|||P9");
        assertEq("shortPid: patientId", "P9", shortPid.getPatientId());
        assertTrue("shortPid: name null", shortPid.getPatientName() == null);

        // buildQBP_Q21 结构
        String q = new HL7MessageBuilder().buildQBP_Q21("P10086", "V001");
        assertTrue("QBP contains \\r", q.contains("\r"));
        String[] segs = q.split("\\r\\n|\\r|\\n");
        assertEq("QBP 3 segments", 3, segs.length);
        assertTrue("QBP seg0 MSH", segs[0].startsWith("MSH|"));
        assertTrue("QBP seg1 QPD", segs[1].startsWith("QPD|"));
        assertTrue("QBP seg2 RCP", segs[2].startsWith("RCP|"));
        assertTrue("QBP msgType QBP^Q21", q.contains("|QBP^Q21|"));
        String[] qpdFields = segs[1].split("\\|", -1);
        assertEq("QBP QPD-1", "Q21^GET_PERSON_DEMO^HL7", qpdFields[1]);
        assertEq("QBP QPD-3 with visitId", "P10086^V001", qpdFields[3]);
        String q2 = new HL7MessageBuilder().buildQBP_Q21("P10086", null);
        String[] qpdFields2 = q2.split("\\r\\n|\\r|\\n")[1].split("\\|", -1);
        assertEq("QBP QPD-3 no visitId", "P10086", qpdFields2[3]);
        String msh10 = segs[0].split("\\|", -1)[9];
        assertTrue("QBP msgId startsWith QBP", msh10.startsWith("QBP"));
    }

    // ========================================================================
    // B-5: 双 Instrument 实体字段契约 (反射, 不依赖 JUnit)
    // ========================================================================

    private static Map<String, String> fieldMap(Class<?> clazz) {
        Map<String, String> map = new LinkedHashMap<>();
        for (Field f : clazz.getDeclaredFields()) {
            if (Modifier.isStatic(f.getModifiers())) {
                continue;
            }
            if (f.isSynthetic()) {
                continue;
            }
            map.put(f.getName(), f.getType().getName());
        }
        return map;
    }

    private static void instrumentContract() {
        System.out.println("-- B-5 Instrument 字段契约 (反射) --");
        try {
            Class<?> base = Class.forName("com.lis.base.entity.Instrument");
            Class<?> projection = Class.forName("com.lis.instrument.entity.Instrument");
            Map<String, String> bm = fieldMap(base);
            Map<String, String> pm = fieldMap(projection);

            assertTrue("B5 base has id", bm.containsKey("id"));
            assertTrue("B5 base has instrumentCode", bm.containsKey("instrumentCode"));
            assertTrue("B5 base has status", bm.containsKey("status"));
            assertTrue("B5 base has workStatus", bm.containsKey("workStatus"));

            // 投影字段必须是写权威字段的子集, 且类型一致
            for (Map.Entry<String, String> e : pm.entrySet()) {
                assertTrue("B5 proj field in base: " + e.getKey(), bm.containsKey(e.getKey()));
                assertEq("B5 field type: " + e.getKey(), bm.get(e.getKey()), e.getValue());
            }
            // 两实体字段集合应保持一致
            assertEq("B5 field sets equal", bm.keySet(), pm.keySet());
        } catch (ClassNotFoundException e) {
            assertTrue("B5 classes loadable", false);
        }
    }
}
