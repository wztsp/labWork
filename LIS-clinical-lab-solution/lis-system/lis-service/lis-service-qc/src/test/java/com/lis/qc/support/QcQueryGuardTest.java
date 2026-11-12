package com.lis.qc.support;

import com.lis.common.core.exception.BusinessException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Constructor;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link QcQueryGuard} 单元测试 —— B-3 (qc 模块 SQL 字符串拼接) 修复验证。
 * <p>
 * 被测类为零 Spring 依赖的纯静态工具, 因此本测试<b>不加载任何容器、不使用 Mock</b>,
 * 直接静态调用即可。覆盖点:
 * <ol>
 *     <li>{@code clampLimit} 全部边界 —— 杜绝 {@code LIMIT} 无上限全表扫;</li>
 *     <li>{@code placeholders} / {@code inClause} 生成的占位符串 —— 替代
 *         {@code IN (1,2,3)} 字符串拼接, 这是 B-3 注入面的根因;</li>
 *     <li>{@code requirePositive} 的异常路径与 400 错误码。</li>
 * </ol>
 */
@DisplayName("B-3 QcQueryGuard 质控查询入参防护")
class QcQueryGuardTest {

    // ==================== clampLimit(Integer) 默认区间 [1,1000] / 缺省 50 ====================

    @Test
    @DisplayName("clampLimit: null 与非正数一律回落到缺省值 50")
    void clampLimit_nullOrNonPositive_fallsBackToDefault() {
        assertEquals(50, QcQueryGuard.clampLimit(null), "null 应回落缺省值");
        assertEquals(50, QcQueryGuard.clampLimit(0), "0 应回落缺省值");
        assertEquals(50, QcQueryGuard.clampLimit(-1), "负数应回落缺省值");
        assertEquals(50, QcQueryGuard.clampLimit(Integer.MIN_VALUE), "极小值应回落缺省值");
    }

    @Test
    @DisplayName("clampLimit: 区间内的值原样返回")
    void clampLimit_withinRange_returnsAsIs() {
        assertEquals(1, QcQueryGuard.clampLimit(1), "下边界 1 原样返回");
        assertEquals(50, QcQueryGuard.clampLimit(50));
        assertEquals(999, QcQueryGuard.clampLimit(999));
        assertEquals(1000, QcQueryGuard.clampLimit(1000), "上边界 1000 原样返回");
    }

    @Test
    @DisplayName("clampLimit: 超过上限截断到 1000 (B-3 防全表扫核心断言)")
    void clampLimit_aboveMax_truncatedToMax() {
        assertEquals(1000, QcQueryGuard.clampLimit(1001), "刚过上限即截断");
        assertEquals(1000, QcQueryGuard.clampLimit(99999));
        assertEquals(1000, QcQueryGuard.clampLimit(Integer.MAX_VALUE), "极大值必须被截断");
    }

    @Test
    @DisplayName("clampLimit: 常量声明符合设计契约")
    void clampLimit_constantsMatchDesign() {
        assertEquals(1, QcQueryGuard.MIN_LIMIT);
        assertEquals(50, QcQueryGuard.DEFAULT_LIMIT);
        assertEquals(1000, QcQueryGuard.MAX_LIMIT);
    }

    // ==================== clampLimit(Integer,int,int) 自定义区间 ====================

    @Test
    @DisplayName("clampLimit(自定义区间): def / max 分支")
    void clampLimit_customRange() {
        assertEquals(20, QcQueryGuard.clampLimit(20, 10, 100), "区间内原样");
        assertEquals(10, QcQueryGuard.clampLimit(null, 10, 100), "null 用自定义缺省");
        assertEquals(10, QcQueryGuard.clampLimit(0, 10, 100), "0 用自定义缺省");
        assertEquals(100, QcQueryGuard.clampLimit(500, 10, 100), "超限截断到自定义上限");
        assertEquals(100, QcQueryGuard.clampLimit(100, 10, 100), "等于上限原样");
    }

    @Test
    @DisplayName("clampLimit(自定义区间): 非法 def/max 自愈, 绝不返回非正数")
    void clampLimit_illegalDefOrMax_selfHeals() {
        // def <= 0 → 回落到类级 DEFAULT_LIMIT(50)
        assertEquals(50, QcQueryGuard.clampLimit(null, 0, 100), "def<=0 时用类级缺省 50");
        assertEquals(50, QcQueryGuard.clampLimit(null, -5, 100));
        // def > max → 被 max 压平
        assertEquals(100, QcQueryGuard.clampLimit(null, 200, 100), "def>max 时压到 max");
        // max <= 0 → 自愈为 MIN_LIMIT(1)
        assertEquals(1, QcQueryGuard.clampLimit(5, 10, 0), "max<=0 自愈为 1");
        assertEquals(1, QcQueryGuard.clampLimit(null, 10, -3), "max 为负同样自愈为 1");
    }

    // ==================== placeholders / inClause —— B-3 注入面根治 ====================

    @Test
    @DisplayName("placeholders: 生成逗号分隔的 ? 串")
    void placeholders_generatesCommaSeparatedMarks() {
        assertEquals("?", QcQueryGuard.placeholders(1));
        assertEquals("?,?", QcQueryGuard.placeholders(2));
        assertEquals("?,?,?", QcQueryGuard.placeholders(3));
        assertEquals("?,?,?,?,?", QcQueryGuard.placeholders(5));
    }

    @Test
    @DisplayName("placeholders: 大批量 size 仍严格 n 个占位符 / n-1 个逗号")
    void placeholders_largeSize_structureHolds() {
        String s = QcQueryGuard.placeholders(100);
        assertEquals(100, s.chars().filter(c -> c == '?').count(), "? 个数必须等于 size");
        assertEquals(99, s.chars().filter(c -> c == ',').count(), "逗号个数必须为 size-1");
    }

    @Test
    @DisplayName("placeholders: size <= 0 抛 BusinessException(400)")
    void placeholders_nonPositive_throws() {
        BusinessException zero = assertThrows(BusinessException.class, () -> QcQueryGuard.placeholders(0));
        assertEquals(400, zero.getCode(), "入参非法必须是 400 而非 500");
        assertThrows(BusinessException.class, () -> QcQueryGuard.placeholders(-1));
    }

    @Test
    @DisplayName("inClause: 生成带括号的 IN 子句片段")
    void inClause_wrapsWithParentheses() {
        assertEquals("(?)", QcQueryGuard.inClause(1));
        assertEquals("(?,?,?)", QcQueryGuard.inClause(3));
    }

    @Test
    @DisplayName("inClause: 产物不含任何字面量数字 (回归 String.format(\"IN (%s)\", ids) 拼接)")
    void inClause_containsNoLiteralValue() {
        String clause = QcQueryGuard.inClause(4);
        assertFalse(clause.matches(".*\\d.*"), "IN 子句中不得出现任何字面量, 实际: " + clause);
        assertTrue(clause.startsWith("(") && clause.endsWith(")"));
    }

    @Test
    @DisplayName("inClause: size <= 0 抛 BusinessException")
    void inClause_nonPositive_throws() {
        assertThrows(BusinessException.class, () -> QcQueryGuard.inClause(0));
        assertThrows(BusinessException.class, () -> QcQueryGuard.inClause(-2));
    }

    // ==================== requirePositive ====================

    @Test
    @DisplayName("requirePositive(Long): 正数原样返回")
    void requirePositiveLong_valid_returnsValue() {
        assertEquals(1L, QcQueryGuard.requirePositive(1L, "lotId"));
        assertEquals(9999L, QcQueryGuard.requirePositive(9999L, "lotId"));
    }

    @Test
    @DisplayName("requirePositive(Long): null / 0 / 负数抛 400, 且提示含字段名")
    void requirePositiveLong_invalid_throws() {
        BusinessException nullEx = assertThrows(BusinessException.class,
                () -> QcQueryGuard.requirePositive((Long) null, "lotId"));
        assertEquals(400, nullEx.getCode());
        assertTrue(nullEx.getMessage().contains("lotId"), "异常提示应含字段名, 实际: " + nullEx.getMessage());

        assertThrows(BusinessException.class, () -> QcQueryGuard.requirePositive(0L, "lotId"));
        assertThrows(BusinessException.class, () -> QcQueryGuard.requirePositive(-1L, "lotId"));
    }

    @Test
    @DisplayName("requirePositive(int): 正数返回 / 非正数抛 400")
    void requirePositiveInt_behaviour() {
        assertEquals(3, QcQueryGuard.requirePositive(3, "limit"));
        BusinessException ex = assertThrows(BusinessException.class,
                () -> QcQueryGuard.requirePositive(0, "limit"));
        assertEquals(400, ex.getCode());
        assertThrows(BusinessException.class, () -> QcQueryGuard.requirePositive(-7, "limit"));
    }

    // ==================== isEmpty ====================

    @Test
    @DisplayName("isEmpty: null 与空集合为空, 非空集合不为空")
    void isEmpty_behaviour() {
        assertTrue(QcQueryGuard.isEmpty(null));
        assertTrue(QcQueryGuard.isEmpty(Collections.emptyList()));
        assertTrue(QcQueryGuard.isEmpty(new ArrayList<>()));
        assertFalse(QcQueryGuard.isEmpty(Arrays.asList(1L, 2L)));
    }

    // ==================== 工具类结构约束 ====================

    @Test
    @DisplayName("类结构: final + 私有构造, 杜绝被实例化/继承后混入状态")
    void classStructure_isFinalUtilityClass() throws Exception {
        assertTrue(Modifier.isFinal(QcQueryGuard.class.getModifiers()), "工具类应为 final");
        Constructor<?>[] ctors = QcQueryGuard.class.getDeclaredConstructors();
        assertEquals(1, ctors.length, "应只有一个构造器");
        assertTrue(Modifier.isPrivate(ctors[0].getModifiers()), "构造器应为 private");
        assertNotNull(ctors[0]);
    }
}
