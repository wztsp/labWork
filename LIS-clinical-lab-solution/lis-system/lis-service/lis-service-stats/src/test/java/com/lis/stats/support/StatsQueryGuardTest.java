package com.lis.stats.support;

import com.lis.common.core.exception.BusinessException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link StatsQueryGuard} 单元测试 —— B-8 (stats 入参零校验) 修复验证。
 * <p>
 * B-8 的真实缺陷不是注入 (SQL 早已 {@code ?} 参数化), 而是<b>入参零校验</b>:
 * {@code startDate="x"} 会一路传到 {@code CAST('x' AS TIMESTAMP)} 在 DB 层炸成 500。
 * 本测试验证校验已前置到 Service 入口, 且非法入参一律转成 400 语义的 {@link BusinessException}。
 */
@DisplayName("B-8 StatsQueryGuard 统计查询入参防护")
class StatsQueryGuardTest {

    // ==================== parseDate ====================

    @Test
    @DisplayName("parseDate: 空值语义为'不过滤', 返回 null 而非抛异常")
    void parseDate_blank_returnsNull() {
        assertNull(StatsQueryGuard.parseDate(null, "startDate"));
        assertNull(StatsQueryGuard.parseDate("", "startDate"));
        assertNull(StatsQueryGuard.parseDate("   ", "startDate"));
        assertNull(StatsQueryGuard.parseDate("\t\n", "startDate"));
    }

    @Test
    @DisplayName("parseDate: 合法 yyyy-MM-dd 正确解析, 并自动 trim")
    void parseDate_valid_parsed() {
        assertEquals(LocalDate.of(2026, 8, 15), StatsQueryGuard.parseDate("2026-08-15", "startDate"));
        assertEquals(LocalDate.of(2026, 8, 15), StatsQueryGuard.parseDate("  2026-08-15  ", "startDate"),
                "首尾空白应被 trim 后解析");
        assertEquals(LocalDate.of(2000, 1, 1), StatsQueryGuard.parseDate("2000-01-01", "endDate"));
        assertEquals(LocalDate.of(2024, 2, 29), StatsQueryGuard.parseDate("2024-02-29", "endDate"),
                "闰年 2 月 29 日必须合法");
    }

    @Test
    @DisplayName("parseDate: 非法格式抛 BusinessException(400), 提示含字段名与格式说明 (B-8 核心)")
    void parseDate_illegalFormat_throws400() {
        BusinessException ex = assertThrows(BusinessException.class,
                () -> StatsQueryGuard.parseDate("x", "startDate"));
        assertEquals(400, ex.getCode(), "入参非法必须转 400, 而非 DB 冒泡的 500");
        assertTrue(ex.getMessage().contains("startDate"), "提示应含字段名, 实际: " + ex.getMessage());
        assertTrue(ex.getMessage().contains("yyyy-MM-dd"), "提示应含期望格式, 实际: " + ex.getMessage());

        assertThrows(BusinessException.class, () -> StatsQueryGuard.parseDate("2026/08/15", "startDate"));
        assertThrows(BusinessException.class, () -> StatsQueryGuard.parseDate("20260815", "startDate"));
        assertThrows(BusinessException.class, () -> StatsQueryGuard.parseDate("2026-8-1", "startDate"));
        assertThrows(BusinessException.class, () -> StatsQueryGuard.parseDate("2026-08-15 10:00:00", "startDate"));
    }

    @Test
    @DisplayName("parseDate: STRICT 解析器拒绝'格式对但日期不存在'的值")
    void parseDate_nonExistentDate_throws() {
        assertThrows(BusinessException.class, () -> StatsQueryGuard.parseDate("2026-13-01", "startDate"),
                "13 月不存在");
        assertThrows(BusinessException.class, () -> StatsQueryGuard.parseDate("2026-02-30", "startDate"),
                "2 月 30 日不存在");
        assertThrows(BusinessException.class, () -> StatsQueryGuard.parseDate("2025-02-29", "startDate"),
                "2025 非闰年, 无 2 月 29 日");
        assertThrows(BusinessException.class, () -> StatsQueryGuard.parseDate("2026-00-10", "startDate"),
                "0 月不存在");
    }

    @Test
    @DisplayName("parseDate: 疑似注入载荷被格式校验拦截")
    void parseDate_injectionPayload_rejected() {
        assertThrows(BusinessException.class,
                () -> StatsQueryGuard.parseDate("2026-08-15' OR '1'='1", "startDate"));
        assertThrows(BusinessException.class,
                () -> StatsQueryGuard.parseDate("2026-08-15; DROP TABLE t", "startDate"));
    }

    // ==================== checkRange ====================

    @Test
    @DisplayName("checkRange: 任一端为 null 视为该端不设限, 直接放行")
    void checkRange_nullEnd_skipped() {
        assertDoesNotThrow(() -> StatsQueryGuard.checkRange(null, LocalDate.of(2026, 1, 1)));
        assertDoesNotThrow(() -> StatsQueryGuard.checkRange(LocalDate.of(2026, 1, 1), null));
        assertDoesNotThrow(() -> StatsQueryGuard.checkRange(null, null));
    }

    @Test
    @DisplayName("checkRange: 起止倒挂抛 BusinessException(400)")
    void checkRange_inverted_throws() {
        BusinessException ex = assertThrows(BusinessException.class,
                () -> StatsQueryGuard.checkRange(LocalDate.of(2026, 8, 15), LocalDate.of(2026, 8, 1)));
        assertEquals(400, ex.getCode());
        assertTrue(ex.getMessage().contains("不得晚于"), "提示应说明倒挂, 实际: " + ex.getMessage());
    }

    @Test
    @DisplayName("checkRange: 同一天 (跨度 1) 合法")
    void checkRange_sameDay_ok() {
        LocalDate d = LocalDate.of(2026, 8, 15);
        assertDoesNotThrow(() -> StatsQueryGuard.checkRange(d, d));
    }

    @Test
    @DisplayName("checkRange: 跨度上下边界 —— 366 天通过 / 367 天抛异常 (B-8 防全表扫)")
    void checkRange_spanBoundary() {
        LocalDate start = LocalDate.of(2026, 1, 1);
        // 含首尾: between=365 → span=366, 恰好等于上限, 应通过
        assertDoesNotThrow(() -> StatsQueryGuard.checkRange(start, start.plusDays(365)),
                "跨度恰为 366 天应通过");
        // between=366 → span=367, 超限
        BusinessException ex = assertThrows(BusinessException.class,
                () -> StatsQueryGuard.checkRange(start, start.plusDays(366)));
        assertEquals(400, ex.getCode());
        assertTrue(ex.getMessage().contains("367"), "提示应含实际跨度, 实际: " + ex.getMessage());
        // 明显超限
        assertThrows(BusinessException.class,
                () -> StatsQueryGuard.checkRange(start, start.plusYears(10)));
    }

    @Test
    @DisplayName("checkRange(自定义 maxDays): maxDays<=0 表示不限跨度, 但仍校验倒挂")
    void checkRange_customMaxDays() {
        LocalDate start = LocalDate.of(2020, 1, 1);
        LocalDate end = LocalDate.of(2026, 1, 1);
        assertThrows(BusinessException.class, () -> StatsQueryGuard.checkRange(start, end, 30),
                "maxDays=30 时 6 年跨度必须被拒");
        assertDoesNotThrow(() -> StatsQueryGuard.checkRange(start, end, 0), "maxDays=0 表示不限跨度");
        assertDoesNotThrow(() -> StatsQueryGuard.checkRange(start, end, -1), "maxDays 为负同样不限跨度");
        assertThrows(BusinessException.class, () -> StatsQueryGuard.checkRange(end, start, 0),
                "不限跨度也不能放过倒挂");
    }

    @Test
    @DisplayName("checkRange(自定义 maxDays): 恰好等于 maxDays 通过, 多一天即拒")
    void checkRange_customMaxDays_boundary() {
        LocalDate start = LocalDate.of(2026, 3, 1);
        assertDoesNotThrow(() -> StatsQueryGuard.checkRange(start, start.plusDays(29), 30));
        assertThrows(BusinessException.class, () -> StatsQueryGuard.checkRange(start, start.plusDays(30), 30));
    }

    // ==================== clampLimit ====================

    @Test
    @DisplayName("clampLimit: null / 非正数回落缺省 100")
    void clampLimit_nullOrNonPositive() {
        assertEquals(100, StatsQueryGuard.clampLimit(null));
        assertEquals(100, StatsQueryGuard.clampLimit(0));
        assertEquals(100, StatsQueryGuard.clampLimit(-10));
    }

    @Test
    @DisplayName("clampLimit: 区间内原样 / 超上限截断到 1000")
    void clampLimit_boundary() {
        assertEquals(1, StatsQueryGuard.clampLimit(1));
        assertEquals(100, StatsQueryGuard.clampLimit(100));
        assertEquals(1000, StatsQueryGuard.clampLimit(1000), "上边界原样");
        assertEquals(1000, StatsQueryGuard.clampLimit(1001), "刚过上限即截断");
        assertEquals(1000, StatsQueryGuard.clampLimit(Integer.MAX_VALUE));
    }

    @Test
    @DisplayName("clampLimit: 常量符合设计契约")
    void clampLimit_constants() {
        assertEquals(100, StatsQueryGuard.DEFAULT_LIMIT);
        assertEquals(1000, StatsQueryGuard.MAX_LIMIT);
        assertEquals(366, StatsQueryGuard.DEFAULT_MAX_RANGE_DAYS);
        assertEquals(64, StatsQueryGuard.MAX_DIM_LENGTH);
        assertEquals("yyyy-MM-dd", StatsQueryGuard.DATE_PATTERN);
    }

    @Test
    @DisplayName("clampLimit(自定义区间): def / max 分支与自愈")
    void clampLimit_customRange() {
        assertEquals(50, StatsQueryGuard.clampLimit(50, 20, 200));
        assertEquals(20, StatsQueryGuard.clampLimit(null, 20, 200));
        assertEquals(200, StatsQueryGuard.clampLimit(999, 20, 200));
        assertEquals(100, StatsQueryGuard.clampLimit(null, 0, 200), "def<=0 用类级缺省 100");
        assertEquals(1, StatsQueryGuard.clampLimit(5, 10, 0), "max<=0 自愈为 1");
    }

    // ==================== checkDim ====================

    @Test
    @DisplayName("checkDim: 空值返回 null, 正常值 trim 后返回")
    void checkDim_blankAndTrim() {
        assertNull(StatsQueryGuard.checkDim(null));
        assertNull(StatsQueryGuard.checkDim(""));
        assertNull(StatsQueryGuard.checkDim("   "));
        assertEquals("血清", StatsQueryGuard.checkDim("  血清  "));
        assertEquals("SERUM", StatsQueryGuard.checkDim("SERUM"));
    }

    @Test
    @DisplayName("checkDim: 长度边界 —— 64 通过, 65 抛异常")
    void checkDim_lengthBoundary() {
        String len64 = "a".repeat(64);
        assertEquals(len64, StatsQueryGuard.checkDim(len64), "恰好 64 应通过");
        String len65 = "a".repeat(65);
        BusinessException ex = assertThrows(BusinessException.class, () -> StatsQueryGuard.checkDim(len65));
        assertEquals(400, ex.getCode());
        assertTrue(ex.getMessage().contains("65"), "提示应含实际长度, 实际: " + ex.getMessage());
    }

    @Test
    @DisplayName("checkDim(白名单): 命中返回值, 未命中抛 400")
    void checkDim_whitelist() {
        Set<String> allowed = new HashSet<>();
        allowed.add("SERUM");
        allowed.add("PLASMA");
        assertEquals("SERUM", StatsQueryGuard.checkDim("SERUM", allowed));
        assertEquals("PLASMA", StatsQueryGuard.checkDim("  PLASMA  ", allowed), "trim 后再比对白名单");

        BusinessException ex = assertThrows(BusinessException.class,
                () -> StatsQueryGuard.checkDim("BLOOD", allowed));
        assertEquals(400, ex.getCode());
        assertTrue(ex.getMessage().contains("BLOOD"));
    }

    @Test
    @DisplayName("checkDim(白名单): 白名单为 null/空时退化为仅长度校验; 入参为空直接返回 null")
    void checkDim_whitelistDegraded() {
        assertEquals("ANY", StatsQueryGuard.checkDim("ANY", null));
        assertEquals("ANY", StatsQueryGuard.checkDim("ANY", Collections.emptySet()));
        assertNull(StatsQueryGuard.checkDim(null, Collections.singleton("SERUM")));
        assertNull(StatsQueryGuard.checkDim("  ", Collections.singleton("SERUM")));
    }

    // ==================== resolveColumn (动态列名白名单) ====================

    @Test
    @DisplayName("resolveColumn: 命中白名单返回真实列名, 空值返回缺省列")
    void resolveColumn_hit() {
        Map<String, String> wl = new HashMap<>();
        wl.put("dept", "apply_dept");
        wl.put("doctor", "apply_doctor");
        assertEquals("apply_dept", StatsQueryGuard.resolveColumn("dept", wl, "def_col"));
        assertEquals("apply_doctor", StatsQueryGuard.resolveColumn("  doctor  ", wl, "def_col"));
        assertEquals("def_col", StatsQueryGuard.resolveColumn(null, wl, "def_col"));
        assertEquals("def_col", StatsQueryGuard.resolveColumn("   ", wl, "def_col"));
    }

    @Test
    @DisplayName("resolveColumn: 未命中白名单必须抛异常 —— 列名无法参数化, 严禁拼接外部输入")
    void resolveColumn_miss_throws() {
        Map<String, String> wl = new HashMap<>();
        wl.put("dept", "apply_dept");
        BusinessException ex = assertThrows(BusinessException.class,
                () -> StatsQueryGuard.resolveColumn("evil", wl, "def_col"));
        assertEquals(400, ex.getCode());
        assertThrows(BusinessException.class,
                () -> StatsQueryGuard.resolveColumn("id; DROP TABLE lis_sample", wl, "def_col"),
                "注入载荷必须被白名单拒绝");
        assertThrows(BusinessException.class,
                () -> StatsQueryGuard.resolveColumn("dept", null, "def_col"),
                "白名单为 null 时任何非空 key 都应被拒");
    }

    // ==================== requirePositive / checkOptionalId ====================

    @Test
    @DisplayName("requirePositive: 正数返回 / null 与非正数抛 400")
    void requirePositive_behaviour() {
        assertEquals(7L, StatsQueryGuard.requirePositive(7L, "deptId"));
        BusinessException ex = assertThrows(BusinessException.class,
                () -> StatsQueryGuard.requirePositive(null, "deptId"));
        assertEquals(400, ex.getCode());
        assertTrue(ex.getMessage().contains("deptId"));
        assertThrows(BusinessException.class, () -> StatsQueryGuard.requirePositive(0L, "deptId"));
        assertThrows(BusinessException.class, () -> StatsQueryGuard.requirePositive(-1L, "deptId"));
    }

    @Test
    @DisplayName("checkOptionalId: null 放行, 非 null 必须为正")
    void checkOptionalId_behaviour() {
        assertNull(StatsQueryGuard.checkOptionalId(null, "deptId"));
        assertEquals(5L, StatsQueryGuard.checkOptionalId(5L, "deptId"));
        assertThrows(BusinessException.class, () -> StatsQueryGuard.checkOptionalId(0L, "deptId"));
        assertThrows(BusinessException.class, () -> StatsQueryGuard.checkOptionalId(-9L, "deptId"));
    }
}
