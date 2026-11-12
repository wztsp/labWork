package com.lis.micro.interpret;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link BreakpointExpr} 折点/原始值表达式解析单元测试。
 *
 * <p>纯值对象, 无任何 Spring / DB 依赖, 直接静态调用即可。
 * 数值断言一律使用 {@link BigDecimal#compareTo}, 禁止 {@code double} / {@code equals}。</p>
 */
class BreakpointExprTest {

    private static BigDecimal bd(String v) {
        return new BigDecimal(v);
    }

    // ==================== 合法表达式解析 ====================

    @Test
    @DisplayName("parse: ≤2 → op=LE, value=2")
    void parse_lessOrEqualUnicode_returnsLeOperator() {
        BreakpointExpr expr = BreakpointExpr.parse("≤2");

        assertTrue(expr.isValid());
        assertEquals(BreakpointExpr.Op.LE, expr.getOp());
        assertEquals(0, expr.getValue().compareTo(bd("2")));
        assertTrue(expr.isUpperBound());
        assertFalse(expr.isLowerBound());
    }

    @Test
    @DisplayName("parse: >=8 (半角) → op=GE, value=8")
    void parse_greaterOrEqualHalfWidth_returnsGeOperator() {
        BreakpointExpr expr = BreakpointExpr.parse(">=8");

        assertTrue(expr.isValid());
        assertEquals(BreakpointExpr.Op.GE, expr.getOp());
        assertEquals(0, expr.getValue().compareTo(bd("8")));
        assertTrue(expr.isLowerBound());
        assertFalse(expr.isUpperBound());
    }

    @Test
    @DisplayName("parse: ≥8 (Unicode) 与 >=8 (半角) 语义等价")
    void parse_unicodeAndHalfWidthGe_areEquivalent() {
        BreakpointExpr unicode = BreakpointExpr.parse("≥8");
        BreakpointExpr halfWidth = BreakpointExpr.parse(">=8");

        assertEquals(unicode.getOp(), halfWidth.getOp());
        assertEquals(0, unicode.getValue().compareTo(halfWidth.getValue()));
    }

    @Test
    @DisplayName("parse: <0.5 → op=LT, value=0.5 (小数)")
    void parse_lessThanDecimal_returnsLtOperator() {
        BreakpointExpr expr = BreakpointExpr.parse("<0.5");

        assertTrue(expr.isValid());
        assertEquals(BreakpointExpr.Op.LT, expr.getOp());
        assertEquals(0, expr.getValue().compareTo(bd("0.5")));
    }

    @Test
    @DisplayName("parse: 4 (无操作符) → op=NONE, value=4")
    void parse_bareNumber_returnsNoneOperator() {
        BreakpointExpr expr = BreakpointExpr.parse("4");

        assertTrue(expr.isValid());
        assertEquals(BreakpointExpr.Op.NONE, expr.getOp());
        assertEquals(0, expr.getValue().compareTo(bd("4")));
        assertFalse(expr.isUpperBound());
        assertFalse(expr.isLowerBound());
    }

    @Test
    @DisplayName("parse: =4 → op=EQ")
    void parse_equalsSign_returnsEqOperator() {
        BreakpointExpr expr = BreakpointExpr.parse("=4");

        assertTrue(expr.isValid());
        assertEquals(BreakpointExpr.Op.EQ, expr.getOp());
        assertEquals(0, expr.getValue().compareTo(bd("4")));
    }

    @Test
    @DisplayName("parse: 全角 ＜２．５ 归一化为半角 <2.5")
    void parse_fullWidthCharacters_normalizedToHalfWidth() {
        BreakpointExpr expr = BreakpointExpr.parse("＜２．５");

        assertTrue(expr.isValid());
        assertEquals(BreakpointExpr.Op.LT, expr.getOp());
        assertEquals(0, expr.getValue().compareTo(bd("2.5")));
    }

    @Test
    @DisplayName("parse: 含空白 ' ≤ 2 ' 去空白后正常解析")
    void parse_withWhitespace_trimmedAndParsed() {
        BreakpointExpr expr = BreakpointExpr.parse("  ≤ 2 ");

        assertTrue(expr.isValid());
        assertEquals(BreakpointExpr.Op.LE, expr.getOp());
        assertEquals(0, expr.getValue().compareTo(bd("2")));
    }

    // ==================== 非法表达式 ====================

    @ParameterizedTest(name = "parse: 非法串 [{0}] → isValid=false")
    @ValueSource(strings = {"≤abc", "abc", "NG", "未检测", "≥", "<>", "2.3.4", "--5"})
    @DisplayName("parse: 各类非法串统一返回 invalid, 不抛异常")
    void parse_illegalExpressions_returnInvalidWithoutThrowing(String raw) {
        BreakpointExpr expr = BreakpointExpr.parse(raw);

        assertFalse(expr.isValid(), "期望非法: " + raw);
        assertNull(expr.getValue());
        assertFalse(expr.matches(bd("1")));
    }

    @Test
    @DisplayName("parse: null 与空串返回 invalid")
    void parse_nullOrEmpty_returnsInvalid() {
        assertFalse(BreakpointExpr.parse(null).isValid());
        assertFalse(BreakpointExpr.parse("").isValid());
        assertFalse(BreakpointExpr.parse("   ").isValid());
    }

    // ==================== matches 区间判定 ====================

    @ParameterizedTest(name = "matches: [{0}] 对 {1} → {2}")
    @CsvSource({
            // LE: 小于等于, 闭区间
            "≤2,   1,   true",
            "≤2,   2,   true",
            "≤2,   3,   false",
            // LT: 严格小于
            "<2,   1.99, true",
            "<2,   2,    false",
            // GE: 大于等于, 闭区间
            "≥8,   8,    true",
            "≥8,   16,   true",
            "≥8,   4,    false",
            // GT: 严格大于
            ">8,   8,    false",
            ">8,   8.01, true",
            // NONE / EQ: 仅相等命中
            "4,    4,    true",
            "4,    5,    false",
            "=4,   4,    true",
            "=4,   3,    false"
    })
    @DisplayName("matches: 各操作符区间判定 (BigDecimal.compareTo)")
    void matches_variousOperators_returnExpected(String raw, String value, boolean expected) {
        BreakpointExpr expr = BreakpointExpr.parse(raw);

        assertTrue(expr.isValid(), "夹具表达式应可解析: " + raw);
        assertEquals(expected, expr.matches(bd(value)),
                () -> "表达式 " + raw + " 对 " + value + " 的判定不符预期");
    }

    @Test
    @DisplayName("matches: 传入 null 数值恒为 false")
    void matches_nullValue_returnsFalse() {
        assertFalse(BreakpointExpr.parse("≤2").matches(null));
    }

    @Test
    @DisplayName("matches: 标度不同但数值相等 (2 与 2.00) 视为相等")
    void matches_differentScaleSameValue_treatedEqual() {
        assertTrue(BreakpointExpr.parse("≤2").matches(bd("2.00")));
        assertTrue(BreakpointExpr.parse("≥2.0").matches(bd("2")));
    }

    @Test
    @DisplayName("invalid: 工厂方法构造的非法实例保留原始串")
    void invalid_factoryMethod_keepsRawAndIsNotValid() {
        BreakpointExpr expr = BreakpointExpr.invalid("≤abc");

        assertFalse(expr.isValid());
        assertEquals("≤abc", expr.getRaw());
        assertNull(expr.getValue());
        assertFalse(expr.isUpperBound());
        assertFalse(expr.isLowerBound());
    }

    @Test
    @DisplayName("getRaw: 保留调用方原始输入 (含空白), 便于 basis 文案回显")
    void getRaw_preservesOriginalInput() {
        assertEquals(" ≤2 ", BreakpointExpr.parse(" ≤2 ").getRaw());
    }
}
