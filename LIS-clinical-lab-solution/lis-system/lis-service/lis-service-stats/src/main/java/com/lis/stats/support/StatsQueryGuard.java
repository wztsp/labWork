package com.lis.stats.support;

import com.lis.common.core.exception.BusinessException;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.format.ResolverStyle;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import java.util.Set;

/**
 * 统计查询入参防护工具。
 * <p>
 * <b>设计约束</b>: 本类为<b>零 Spring 依赖的纯静态工具类</b>, 不持有任何协作者, 可脱离容器直接单测。
 * <p>
 * B-8 修复背景: stats 模块的 SQL 早已全部使用 {@code ?} 参数化 (无注入), 真实缺陷是
 * <b>入参零校验</b> —— {@code startDate="x"} 会一路传到 {@code CAST('x' AS TIMESTAMP)}
 * 抛 PSQLException 冒泡成 500; 且日期区间与 {@code limit} 无上限, 可触发全表扫描。
 * 本类把校验前置到 Service 入口, 非法入参一律抛 {@link BusinessException}
 * (由 lis-common 的 GlobalExceptionHandler 统一转 400, 前端可读)。
 *
 * @see StatsSqlSupport
 */
public final class StatsQueryGuard {

    /** 对外 API 日期串格式 */
    public static final String DATE_PATTERN = "yyyy-MM-dd";
    /** limit 缺省值 */
    public static final int DEFAULT_LIMIT = 100;
    /** limit 上限 */
    public static final int MAX_LIMIT = 1000;
    /** 日期区间跨度上限 (天), 默认一年 (含闰年) */
    public static final int DEFAULT_MAX_RANGE_DAYS = 366;
    /** 维度值 (标本类型等) 允许的最大长度 */
    public static final int MAX_DIM_LENGTH = 64;

    /**
     * 严格解析器: {@code uuuu-MM-dd} + {@link ResolverStyle#STRICT},
     * 可拒绝 {@code 2026-13-01} / {@code 2026-02-30} 这类"格式对但日期不存在"的值。
     */
    private static final DateTimeFormatter STRICT_DATE = DateTimeFormatter
            .ofPattern("uuuu-MM-dd")
            .withResolverStyle(ResolverStyle.STRICT);

    /** 工具类禁止实例化 */
    private StatsQueryGuard() {
    }

    /**
     * 严格解析 {@code yyyy-MM-dd} 日期串。
     *
     * @param value 日期串, null 或空串视为"不过滤", 返回 null
     * @param field 字段名 (用于异常提示, 如 {@code startDate})
     * @return 解析后的日期; 入参为空时返回 null
     * @throws BusinessException 格式非法或日期不存在
     */
    public static LocalDate parseDate(String value, String field) {
        if (value == null || value.trim().isEmpty()) {
            return null;
        }
        String trimmed = value.trim();
        try {
            return LocalDate.parse(trimmed, STRICT_DATE);
        } catch (DateTimeParseException e) {
            throw new BusinessException(400, field + " 格式非法, 应为 " + DATE_PATTERN + ", 当前值: " + trimmed);
        }
    }

    /**
     * 使用默认跨度上限 ({@link #DEFAULT_MAX_RANGE_DAYS}) 校验日期区间。
     *
     * @param start 起始日期, 可为 null
     * @param end   截止日期, 可为 null
     * @throws BusinessException 起止倒挂或跨度超限
     */
    public static void checkRange(LocalDate start, LocalDate end) {
        checkRange(start, end, DEFAULT_MAX_RANGE_DAYS);
    }

    /**
     * 校验日期区间合法性: 起止不得倒挂, 跨度不得超过 maxDays。
     * <p>
     * 任一端为 null 时跳过对应校验 (语义为"该端不设限")。
     *
     * @param start   起始日期, 可为 null
     * @param end     截止日期, 可为 null
     * @param maxDays 允许的最大跨度 (天, 含首尾); &lt;= 0 表示不限制跨度
     * @throws BusinessException 起止倒挂或跨度超限
     */
    public static void checkRange(LocalDate start, LocalDate end, int maxDays) {
        if (start == null || end == null) {
            return;
        }
        if (start.isAfter(end)) {
            throw new BusinessException(400,
                    "startDate 不得晚于 endDate, 当前值: " + start + " ~ " + end);
        }
        if (maxDays <= 0) {
            return;
        }
        long span = ChronoUnit.DAYS.between(start, end) + 1;
        if (span > maxDays) {
            throw new BusinessException(400,
                    "统计区间跨度不得超过 " + maxDays + " 天, 当前跨度: " + span + " 天");
        }
    }

    /**
     * 使用默认区间钳制 limit。
     *
     * @param limit 外部传入的条数, 可为 null
     * @return 落在 [1, {@link #MAX_LIMIT}] 内的安全值
     */
    public static int clampLimit(Integer limit) {
        return clampLimit(limit, DEFAULT_LIMIT, MAX_LIMIT);
    }

    /**
     * 钳制 limit 到 [1, max] 区间 (超限直接截断, 不抛异常, 避免破坏既有查询)。
     *
     * @param limit 外部传入的条数, 可为 null
     * @param def   limit 为 null 或 &lt;= 0 时使用的缺省值
     * @param max   允许的最大值
     * @return 落在 [1, max] 内的安全值
     */
    public static int clampLimit(Integer limit, int def, int max) {
        int safeMax = Math.max(1, max);
        int safeDef = def <= 0 ? DEFAULT_LIMIT : def;
        if (safeDef > safeMax) {
            safeDef = safeMax;
        }
        if (limit == null || limit <= 0) {
            return safeDef;
        }
        return Math.min(limit, safeMax);
    }

    /**
     * 校验维度<b>值</b> (作为 {@code ?} 参数绑定, 非列名): 仅做长度校验。
     *
     * @param value 维度值 (如 specimenType), 可为 null/空
     * @return 去除首尾空白后的值; 入参为空时返回 null
     * @throws BusinessException 长度超过 {@link #MAX_DIM_LENGTH}
     */
    public static String checkDim(String value) {
        if (value == null || value.trim().isEmpty()) {
            return null;
        }
        String trimmed = value.trim();
        if (trimmed.length() > MAX_DIM_LENGTH) {
            throw new BusinessException(400,
                    "维度取值长度不得超过 " + MAX_DIM_LENGTH + ", 当前长度: " + trimmed.length());
        }
        return trimmed;
    }

    /**
     * 校验维度值是否在白名单内。
     *
     * @param value   维度值, 可为 null/空
     * @param allowed 允许的取值集合, 为 null/空时退化为仅做长度校验
     * @return 校验通过的值; 入参为空时返回 null
     * @throws BusinessException 长度超限或不在白名单内
     */
    public static String checkDim(String value, Set<String> allowed) {
        String trimmed = checkDim(value);
        if (trimmed == null) {
            return null;
        }
        if (allowed == null || allowed.isEmpty()) {
            return trimmed;
        }
        if (!allowed.contains(trimmed)) {
            throw new BusinessException(400, "不支持的统计维度: " + trimmed);
        }
        return trimmed;
    }

    /**
     * 动态<b>列名</b>白名单映射 —— 列名无法参数化, 必须走白名单, 严禁拼接外部输入。
     *
     * @param value     外部传入的维度键 (如 {@code "dept"})
     * @param whitelist 维度键 → 真实列名 的映射 (代码内常量)
     * @param def       value 为空时使用的缺省列名
     * @return 白名单命中的真实列名
     * @throws BusinessException 白名单未命中
     */
    public static String resolveColumn(String value, Map<String, String> whitelist, String def) {
        if (value == null || value.trim().isEmpty()) {
            return def;
        }
        String key = value.trim();
        if (whitelist == null || !whitelist.containsKey(key)) {
            throw new BusinessException(400, "不支持的统计维度: " + key);
        }
        return whitelist.get(key);
    }

    /**
     * 校验主键类入参必须非空且为正数。
     *
     * @param value 待校验值
     * @param field 字段名 (用于异常提示)
     * @return 原值
     * @throws BusinessException 值为 null 或 &lt;= 0
     */
    public static Long requirePositive(Long value, String field) {
        if (value == null || value <= 0L) {
            throw new BusinessException(400, field + " 必须为正整数, 当前值: " + value);
        }
        return value;
    }

    /**
     * 校验可选的主键类入参: 为 null 时放行, 非 null 时必须为正数。
     *
     * @param value 待校验值, 可为 null
     * @param field 字段名 (用于异常提示)
     * @return 原值 (可能为 null)
     * @throws BusinessException 非 null 且 &lt;= 0
     */
    public static Long checkOptionalId(Long value, String field) {
        if (value == null) {
            return null;
        }
        return requirePositive(value, field);
    }
}
