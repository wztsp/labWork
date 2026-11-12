package com.lis.stats.support;

import com.lis.common.core.exception.BusinessException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.sql.Date;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link StatsSqlSupport} 单元测试 —— B-8 SQL 片段装配与列名白名单。
 * <p>
 * 关键契约:
 * <ol>
 *     <li>生成的片段<b>只含 {@code ?} 占位符</b>, 绝不内联任何值 (回归 B-8 定性);</li>
 *     <li>绑定值为强类型 {@link java.sql.Date}, 而非交给 DB 做
 *         {@code CAST('x' AS TIMESTAMP)} 字符串强转;</li>
 *     <li>endDate 语义为"含当天全部时刻", 即右开区间取 endDate+1 天;</li>
 *     <li>列名必须是合法标识符, 任何外部输入都不得作为列名。</li>
 * </ol>
 */
@DisplayName("B-8 StatsSqlSupport 统计 SQL 片段装配")
class StatsSqlSupportTest {

    private static final String COL = "receive_time";

    // ==================== dateRangeFilter ====================

    @Test
    @DisplayName("dateRangeFilter: 起止都给出时生成双边条件, 且 end 取 +1 天 (右开区间)")
    void dateRangeFilter_bothEnds() {
        List<Object> args = new ArrayList<>();
        String sql = StatsSqlSupport.dateRangeFilter(COL,
                LocalDate.of(2026, 8, 1), LocalDate.of(2026, 8, 31), args);

        assertEquals(" AND receive_time >= ? AND receive_time < ?", sql);
        assertEquals(2, args.size(), "应绑定 2 个参数");
        assertEquals(Date.valueOf(LocalDate.of(2026, 8, 1)), args.get(0), "下界为 startDate 当天");
        assertEquals(Date.valueOf(LocalDate.of(2026, 9, 1)), args.get(1),
                "上界必须是 endDate+1 天, 否则 endDate 当天数据会被漏掉");
    }

    @Test
    @DisplayName("dateRangeFilter: 只有起始日期")
    void dateRangeFilter_startOnly() {
        List<Object> args = new ArrayList<>();
        String sql = StatsSqlSupport.dateRangeFilter(COL, LocalDate.of(2026, 8, 1), null, args);
        assertEquals(" AND receive_time >= ?", sql);
        assertEquals(1, args.size());
        assertEquals(Date.valueOf("2026-08-01"), args.get(0));
    }

    @Test
    @DisplayName("dateRangeFilter: 只有截止日期")
    void dateRangeFilter_endOnly() {
        List<Object> args = new ArrayList<>();
        String sql = StatsSqlSupport.dateRangeFilter(COL, null, LocalDate.of(2026, 8, 31), args);
        assertEquals(" AND receive_time < ?", sql);
        assertEquals(1, args.size());
        assertEquals(Date.valueOf("2026-09-01"), args.get(0));
    }

    @Test
    @DisplayName("dateRangeFilter: 起止均为 null 时不生成任何片段, 不污染 args")
    void dateRangeFilter_bothNull() {
        List<Object> args = new ArrayList<>();
        assertEquals("", StatsSqlSupport.dateRangeFilter(COL, null, null, args));
        assertTrue(args.isEmpty());
    }

    @Test
    @DisplayName("dateRangeFilter: 片段中不得出现任何日期字面量, 只能是 ?")
    void dateRangeFilter_noLiteralInSql() {
        List<Object> args = new ArrayList<>();
        String sql = StatsSqlSupport.dateRangeFilter(COL,
                LocalDate.of(2026, 8, 1), LocalDate.of(2026, 8, 31), args);
        assertTrue(sql.indexOf('\'') < 0, "不得出现引号, 实际: " + sql);
        assertTrue(sql.indexOf("2026") < 0, "不得内联日期字面量, 实际: " + sql);
        assertEquals(2, sql.chars().filter(c -> c == '?').count(), "应恰好 2 个占位符");
    }

    @Test
    @DisplayName("dateRangeFilter: 支持 alias.col 形式列名")
    void dateRangeFilter_aliasColumn() {
        List<Object> args = new ArrayList<>();
        String sql = StatsSqlSupport.dateRangeFilter("s.receive_time", LocalDate.of(2026, 1, 1), null, args);
        assertEquals(" AND s.receive_time >= ?", sql);
    }

    @Test
    @DisplayName("dateRangeFilter: 非法列名 / args 为 null 抛异常")
    void dateRangeFilter_illegalInputs() {
        assertThrows(BusinessException.class,
                () -> StatsSqlSupport.dateRangeFilter("a; DROP TABLE t", LocalDate.now(), null, new ArrayList<>()));
        assertThrows(BusinessException.class,
                () -> StatsSqlSupport.dateRangeFilter(null, LocalDate.now(), null, new ArrayList<>()));
        assertThrows(BusinessException.class,
                () -> StatsSqlSupport.dateRangeFilter(COL, LocalDate.now(), null, null));
    }

    @Test
    @DisplayName("dateRangeFilter: 可对同一个 args 连续追加, 参数顺序与片段顺序一致")
    void dateRangeFilter_accumulatesArgsInOrder() {
        List<Object> args = new ArrayList<>();
        StringBuilder sql = new StringBuilder("SELECT 1 FROM t WHERE 1=1");
        sql.append(StatsSqlSupport.dateRangeFilter(COL, LocalDate.of(2026, 1, 1), null, args));
        sql.append(StatsSqlSupport.eqFilter("specimen_type", "SERUM", args));
        assertEquals("SELECT 1 FROM t WHERE 1=1 AND receive_time >= ? AND specimen_type = ?", sql.toString());
        assertEquals(2, args.size());
        assertEquals(Date.valueOf("2026-01-01"), args.get(0));
        assertEquals("SERUM", args.get(1));
        assertEquals(2, sql.chars().filter(c -> c == '?').count(),
                "占位符个数必须与参数个数严格一致, 否则运行期 JDBC 报错");
    }

    // ==================== eqFilter ====================

    @Test
    @DisplayName("eqFilter: 值非空生成 ' AND col = ?' 并绑定参数")
    void eqFilter_withValue() {
        List<Object> args = new ArrayList<>();
        assertEquals(" AND specimen_type = ?", StatsSqlSupport.eqFilter("specimen_type", "SERUM", args));
        assertEquals(1, args.size());
        assertEquals("SERUM", args.get(0));
    }

    @Test
    @DisplayName("eqFilter: 值为 null 时不生成片段也不绑定参数")
    void eqFilter_nullValue() {
        List<Object> args = new ArrayList<>();
        assertEquals("", StatsSqlSupport.eqFilter("specimen_type", null, args));
        assertTrue(args.isEmpty());
    }

    @Test
    @DisplayName("eqFilter: 带引号的注入型取值只会成为绑定参数, 不进入 SQL 文本")
    void eqFilter_injectionValueStaysAsParameter() {
        List<Object> args = new ArrayList<>();
        String payload = "' OR '1'='1";
        String sql = StatsSqlSupport.eqFilter("specimen_type", payload, args);
        assertEquals(" AND specimen_type = ?", sql, "注入载荷绝不能出现在 SQL 文本里");
        assertEquals(payload, args.get(0), "载荷应原样作为绑定参数");
    }

    @Test
    @DisplayName("eqFilter: 非法列名 / args 为 null 抛异常")
    void eqFilter_illegalInputs() {
        assertThrows(BusinessException.class,
                () -> StatsSqlSupport.eqFilter("1bad", "v", new ArrayList<>()));
        assertThrows(BusinessException.class,
                () -> StatsSqlSupport.eqFilter("col", "v", null));
    }

    // ==================== toSqlDate ====================

    @Test
    @DisplayName("toSqlDate: null 透传, 非 null 转 java.sql.Date")
    void toSqlDate_behaviour() {
        assertNull(StatsSqlSupport.toSqlDate(null));
        assertEquals(Date.valueOf("2026-08-15"), StatsSqlSupport.toSqlDate(LocalDate.of(2026, 8, 15)));
    }

    // ==================== requireIdentifier ====================

    @Test
    @DisplayName("requireIdentifier: 合法标识符通过")
    void requireIdentifier_valid() {
        assertEquals("col", StatsSqlSupport.requireIdentifier("col"));
        assertEquals("_col1", StatsSqlSupport.requireIdentifier("_col1"));
        assertEquals("receive_time", StatsSqlSupport.requireIdentifier("receive_time"));
        assertEquals("s.receive_time", StatsSqlSupport.requireIdentifier("s.receive_time"));
        assertEquals("T1.C_1", StatsSqlSupport.requireIdentifier("T1.C_1"));
    }

    @Test
    @DisplayName("requireIdentifier: 非法标识符一律抛 500 (代码内常量传错属编程错误)")
    void requireIdentifier_invalid() {
        BusinessException ex = assertThrows(BusinessException.class,
                () -> StatsSqlSupport.requireIdentifier("a b"));
        assertEquals(500, ex.getCode(), "列名非法属编程错误, 语义为 500");

        assertThrows(BusinessException.class, () -> StatsSqlSupport.requireIdentifier(null));
        assertThrows(BusinessException.class, () -> StatsSqlSupport.requireIdentifier(""));
        assertThrows(BusinessException.class, () -> StatsSqlSupport.requireIdentifier("1col"),
                "不得以数字开头");
        assertThrows(BusinessException.class, () -> StatsSqlSupport.requireIdentifier("col;DROP"));
        assertThrows(BusinessException.class, () -> StatsSqlSupport.requireIdentifier("col--"));
        assertThrows(BusinessException.class, () -> StatsSqlSupport.requireIdentifier("a.b.c"),
                "最多一级 alias 限定");
        assertThrows(BusinessException.class, () -> StatsSqlSupport.requireIdentifier("col)"));
        assertThrows(BusinessException.class, () -> StatsSqlSupport.requireIdentifier("*"));
    }
}
