package com.lis.qc.support;

import com.lis.common.core.exception.BusinessException;

import java.util.Collection;

/**
 * 质控查询入参防护工具。
 * <p>
 * <b>设计约束</b>: 本类为<b>零 Spring 依赖的纯静态工具类</b>, 不持有任何协作者, 可脱离容器直接单测
 * (静态调用即可, 无需 Mock)。新增校验逻辑一律放这里, 不要散落到 {@code ServiceImpl}。
 * <p>
 * 职责:
 * <ol>
 *     <li>{@link #clampLimit(Integer)} —— 把外部传入的 {@code limit} 钳制到 [1, 1000],
 *         杜绝 {@code LIMIT} 无上限导致的全表扫描 (B-3)</li>
 *     <li>{@link #inClause(int)} / {@link #placeholders(int)} —— 生成 JDBC 参数占位符,
 *         替代 {@code IN (1,2,3)} 字符串拼接, 杜绝 SQL 注入面 (B-3)</li>
 *     <li>{@link #requirePositive(Long, String)} —— 主键类入参非空且为正数校验</li>
 * </ol>
 *
 * @see com.lis.qc.service.impl.QCChartServiceImpl
 * @see com.lis.qc.service.impl.QCRecordServiceImpl
 */
public final class QcQueryGuard {

    /** limit 允许的最小值 */
    public static final int MIN_LIMIT = 1;
    /** limit 缺省值 (limit <= 0 时生效) */
    public static final int DEFAULT_LIMIT = 50;
    /** limit 允许的最大值 (超出直接截断, 不抛异常, 避免破坏既有查询) */
    public static final int MAX_LIMIT = 1000;

    /** 工具类禁止实例化 */
    private QcQueryGuard() {
    }

    /**
     * 使用默认区间 [1, 1000] / 缺省值 50 钳制 limit。
     *
     * @param limit 外部传入的分页条数, 可为 null
     * @return 落在 [{@link #MIN_LIMIT}, {@link #MAX_LIMIT}] 内的安全值
     */
    public static int clampLimit(Integer limit) {
        return clampLimit(limit, DEFAULT_LIMIT, MAX_LIMIT);
    }

    /**
     * 钳制 limit 到 [1, max] 区间。
     *
     * @param limit 外部传入的分页条数, 可为 null
     * @param def   limit 为 null 或 &lt;= 0 时使用的缺省值
     * @param max   允许的最大值 (必须 &gt;= 1)
     * @return 落在 [1, max] 内的安全值
     */
    public static int clampLimit(Integer limit, int def, int max) {
        int safeMax = Math.max(MIN_LIMIT, max);
        int safeDef = def <= 0 ? DEFAULT_LIMIT : def;
        if (safeDef > safeMax) {
            safeDef = safeMax;
        }
        if (limit == null || limit <= 0) {
            return safeDef;
        }
        if (limit > safeMax) {
            return safeMax;
        }
        return limit;
    }

    /**
     * 校验主键类入参必须非空且为正数。
     *
     * @param value 待校验值
     * @param field 字段名 (用于异常提示)
     * @return 原值 (校验通过)
     * @throws BusinessException 值为 null 或 &lt;= 0
     */
    public static Long requirePositive(Long value, String field) {
        if (value == null || value <= 0L) {
            throw new BusinessException(400, field + " 必须为正整数, 当前值: " + value);
        }
        return value;
    }

    /**
     * 校验 int 型入参必须为正数。
     *
     * @param value 待校验值
     * @param field 字段名 (用于异常提示)
     * @return 原值 (校验通过)
     * @throws BusinessException 值 &lt;= 0
     */
    public static int requirePositive(int value, String field) {
        if (value <= 0) {
            throw new BusinessException(400, field + " 必须为正整数, 当前值: " + value);
        }
        return value;
    }

    /**
     * 生成 JDBC 参数占位符串, 例如 count=3 返回 {@code "?,?,?"}。
     *
     * @param count 占位符个数 (必须 &gt;= 1)
     * @return 逗号分隔的占位符串
     * @throws BusinessException count &lt;= 0
     */
    public static String placeholders(int count) {
        if (count <= 0) {
            throw new BusinessException(400, "占位符个数必须为正整数, 当前值: " + count);
        }
        StringBuilder sb = new StringBuilder(count * 2);
        for (int i = 0; i < count; i++) {
            if (i > 0) {
                sb.append(',');
            }
            sb.append('?');
        }
        return sb.toString();
    }

    /**
     * 生成带括号的 IN 子句占位符, 例如 count=3 返回 {@code "(?,?,?)"}。
     * <p>
     * 用法: {@code "SELECT ... WHERE id IN " + QcQueryGuard.inClause(ids.size())},
     * 参数由 {@code jdbcTemplate.query(sql, handler, ids.toArray())} 绑定。
     *
     * @param count 占位符个数 (必须 &gt;= 1)
     * @return 形如 {@code (?,?,?)} 的 IN 子句片段
     * @throws BusinessException count &lt;= 0
     */
    public static String inClause(int count) {
        return "(" + placeholders(count) + ")";
    }

    /**
     * 判断集合是否为空 (null 或 size == 0)。
     *
     * @param values 待判断集合
     * @return true 表示为空
     */
    public static boolean isEmpty(Collection<?> values) {
        return values == null || values.isEmpty();
    }
}
