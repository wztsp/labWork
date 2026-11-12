import com.lis.reagent.support.BizNoGenerator;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * R-05 BizNoGenerator 零依赖可执行自检 Runner。
 *
 * <p>验证对象：{@code lis-service-reagent/.../support/BizNoGenerator.java}，
 * 规则为 {@code 前缀 + yyyyMMdd + 4 位序号}（如 PUR202608040001）。</p>
 *
 * <p>设计约束：不依赖 JUnit / Mockito / Spring，仅依赖 JDK 标准库 +
 * 生产类 {@code BizNoGenerator}（其异常类型来自 lis-common 的 {@code BusinessException}）。</p>
 *
 * <p>运行方式（Windows，分号分隔 classpath，参照 build/selfcheck 既有脚本）：</p>
 * <pre>
 *   javac -encoding UTF-8 -d build/selfcheck/runner-classes \
 *     -cp "build/selfcheck/reagent-classes-final;C:/Users/Administrator/.m2/repository/com/lis/lis-common/1.0.0/lis-common-1.0.0.jar" \
 *     tools/selfcheck/BizNoGeneratorSelfCheck.java
 *   java -cp "build/selfcheck/runner-classes;build/selfcheck/reagent-classes-final;C:/Users/Administrator/.m2/repository/com/lis/lis-common/1.0.0/lis-common-1.0.0.jar" \
 *     BizNoGeneratorSelfCheck
 * </pre>
 */
public final class BizNoGeneratorSelfCheck {

    private static final LocalDate D1 = LocalDate.of(2026, 8, 4);
    private static final String H1 = "PUR20260804";

    private static int total = 0;
    private static int passed = 0;
    private static int failed = 0;
    private static final List<String> failures = new ArrayList<>();

    public static void main(String[] args) {
        System.out.println("========== R-05 BizNoGenerator 零依赖自检 Runner ==========");

        // ---- 1. 首号 / 续号 / 长度固定 ----
        nextNoBasics();
        // ---- 2. parseSeq 解析 ----
        parseSeqCases();
        // ---- 3. 进位行为（9999 用尽必须抛异常，不得进位到 5 位） ----
        carryBehavior();
        // ---- 4. 参数守卫 ----
        guardCases();
        // ---- 5. 前缀隔离 / 多日期隔离 ----
        isolationCases();
        // ---- 6. extractTrailingDigits ----
        extractTrailingDigitsCases();

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
    // 断言工具（极简，零依赖）
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

    private static String fixedHead(String prefix, LocalDate date) {
        return prefix + String.format("%04d%02d%02d", date.getYear(), date.getMonthValue(), date.getDayOfMonth());
    }

    // ========================================================================
    // 1. 首号 / 续号 / 长度固定
    // ========================================================================

    private static void nextNoBasics() {
        System.out.println("-- 1. 首号 / 续号 / 长度固定 --");
        // 当日无单据：取 0001
        assertEq("first no", H1 + "0001",
                BizNoGenerator.nextNo("PUR", D1, head -> null));
        // 已存在 0007：续为 0008
        assertEq("continuation", H1 + "0008",
                BizNoGenerator.nextNo("PUR", D1, head -> H1 + "0007"));
        // 已存在 0012：续为 0013
        assertEq("continuation2", H1 + "0013",
                BizNoGenerator.nextNo("PUR", D1, head -> H1 + "0012"));
        // 长度恒定：前缀(3) + 日期(8) + 序号(4) = 15
        String no = BizNoGenerator.nextNo("PUR", D1, head -> H1 + "0042");
        assertEq("length=15", 15, no.length());
        // 序号部分恒 4 位（前导补零）
        assertTrue("zero-padded 4 digits", no.endsWith("0043"));
        // 连续两次生成：模拟回调返回上一步结果，序号严格 +1
        String[] holder = {H1 + "0100"};
        String n1 = BizNoGenerator.nextNo("PUR", D1, head -> holder[0]);
        String n2 = BizNoGenerator.nextNo("PUR", D1, head -> holder[0]);
        assertEq("two calls same max -> same no", n1, n2);
        holder[0] = n1;
        String n3 = BizNoGenerator.nextNo("PUR", D1, head -> holder[0]);
        assertEq("sequential increment", H1 + "0102", n3);
    }

    // ========================================================================
    // 2. parseSeq 解析
    // ========================================================================

    private static void parseSeqCases() {
        System.out.println("-- 2. parseSeq 解析 --");
        assertEq("parseSeq(null)=0", 0, BizNoGenerator.parseSeq(null, H1));
        assertEq("parseSeq(head null)=0", 0, BizNoGenerator.parseSeq(H1 + "0005", null));
        assertEq("parseSeq(0005)=5", 5, BizNoGenerator.parseSeq(H1 + "0005", H1));
        assertEq("parseSeq(0999)=999", 999, BizNoGenerator.parseSeq(H1 + "0999", H1));
        // MAX 值解析
        assertEq("parseSeq(9999)=9999", 9999, BizNoGenerator.parseSeq(H1 + "9999", H1));
        // 前缀不同 -> 0（隔离性）
        assertEq("parseSeq(otherPrefix)=0", 0, BizNoGenerator.parseSeq("REQ202608040005", H1));
        // 头部不匹配（同前缀不同日期）-> 0
        assertEq("parseSeq(otherDate)=0", 0, BizNoGenerator.parseSeq("PUR202608050005", H1));
        // 尾部非数字 -> 0
        assertEq("parseSeq(nonDigit tail)=0", 0, BizNoGenerator.parseSeq(H1 + "0005A", H1));
        assertEq("parseSeq(empty tail)=0", 0, BizNoGenerator.parseSeq(H1, H1));
        // 空白容忍：trim 后为合法单号，应正确解析（实现做了 trim，属健壮行为）
        assertEq("parseSeq(blank tolerated)=3", 3, BizNoGenerator.parseSeq("  " + H1 + "0003  ", H1));
    }

    // ========================================================================
    // 3. 进位行为：9999 用尽必须抛异常，不得进位到 5 位
    // ========================================================================

    private static void carryBehavior() {
        System.out.println("-- 3. 进位行为 --");
        // 已存在 9999：nextNo 必须抛「用尽」，绝不生成 5 位序号
        assertThrows("carry@9999 throws", () ->
                BizNoGenerator.nextNo("PUR", D1, head -> H1 + "9999"));
        // 已存在 9998：正常续为 9999（仍 4 位）
        assertEq("before carry =9999", H1 + "9999",
                BizNoGenerator.nextNo("PUR", D1, head -> H1 + "9998"));
        // 进位后的下一张（9999 已存在）应仍抛异常
        assertThrows("next after 9999 throws", () ->
                BizNoGenerator.nextNo("PUR", D1, head -> H1 + "9999"));
    }

    // ========================================================================
    // 4. 参数守卫
    // ========================================================================

    private static void guardCases() {
        System.out.println("-- 4. 参数守卫 --");
        assertThrows("empty prefix throws", () ->
                BizNoGenerator.nextNo("", D1, head -> null));
        assertThrows("null prefix throws", () ->
                BizNoGenerator.nextNo(null, D1, head -> null));
        assertThrows("null fetcher throws", () ->
                BizNoGenerator.nextNo("PUR", D1, null));
        // null 日期按当天兜底（不抛异常即可）
        String todayNo = BizNoGenerator.nextNo("PUR", null, head -> null);
        assertEq("null date -> today, length=15", 15, todayNo.length());
    }

    // ========================================================================
    // 5. 前缀隔离 / 多日期隔离
    // ========================================================================

    private static void isolationCases() {
        System.out.println("-- 5. 前缀隔离 / 日期隔离 --");
        // 同日期下，PUR 的 MAX 不影响 REQ
        assertEq("prefix isolation REQ", "REQ202608040001",
                BizNoGenerator.nextNo("REQ", D1, head -> "PUR202608040007"));
        // 不同日期互不影响
        LocalDate d2 = D1.plusDays(1);
        assertEq("date isolation", "PUR202608050001",
                BizNoGenerator.nextNo("PUR", d2, head -> H1 + "0007"));
        // 四种业务前缀都能生成
        for (String p : new String[]{"PUR", "REQ", "INB", "PA"}) {
            String head = fixedHead(p, D1);
            String no = BizNoGenerator.nextNo(p, D1, h -> head + "0099");
            assertTrue("prefix " + p + " = 0100", no.equals(head + "0100"));
        }
    }

    // ========================================================================
    // 6. extractTrailingDigits
    // ========================================================================

    private static void extractTrailingDigitsCases() {
        System.out.println("-- 6. extractTrailingDigits --");
        // 语义：提取「尾部数字序列」（≥4 位），匹配整个数字尾，而非仅序号。
        // 该方法当前无业务调用方（仅工具性复用），故按实现语义断言。
        assertEq("extract null=null", null, BizNoGenerator.extractTrailingDigits(null));
        // 尾部无 ≥4 位数字序列 -> null（如纯字母、3 位数字）
        assertEq("extract no digit tail=null", null, BizNoGenerator.extractTrailingDigits("PUR"));
        assertEq("extract 3-digit tail=null", null, BizNoGenerator.extractTrailingDigits("abc123"));
        // 日期本身也是数字尾，会被整体提取（符合"尾部数字序列"语义）
        assertEq("extract date-only", "20260804", BizNoGenerator.extractTrailingDigits("PUR20260804"));
        // 带序号时提取完整数字尾
        assertEq("extract full tail", "202608040012", BizNoGenerator.extractTrailingDigits("PUR202608040012"));
        assertEq("extract 5-digit seq", "2026080410000", BizNoGenerator.extractTrailingDigits("PUR2026080410000"));
    }
}
