package com.lis.stats.support;

import com.lis.common.core.exception.BusinessException;

import java.sql.Date;
import java.time.LocalDate;
import java.util.List;
import java.util.regex.Pattern;

/**
 * 统计 SQL 片段装配工具。
 * <p>
 * <b>设计约束</b>: 零 Spring 依赖的纯静态工具类, 可脱离容器直接单测。
 * <p>
 * B-8 修复背景: 原先 {@code dateRangeFilter} 在 {@code WorkloadStatsServiceImpl} /
 * {@code ChargeStatsServiceImpl} / {@code CriticalStatsServiceImpl} 三处复制粘贴,
 * 且用 {@code CAST(? AS TIMESTAMP)} 把<b>字符串</b>丢给数据库强转 —— 非法日期串直接
 * 在 DB 层炸成 500。现统一收敛到本类, 且改为绑定强类型 {@link java.sql.Date}
 * (调用方先经 {@link StatsQueryGuard#parseDate(String, String)} 解析为 {@link LocalDate}),
 * 从根上杜绝"字符串强转失败"。
 * <p>
 * <b>安全约定</b>: {@code column} 参数只允许来自代码内常量, 本类仍会做标识符白名单式格式校验,
 * 任何外部输入都不得直接作为列名传入。
 *
 * @see StatsQueryGuard
 */
public final class StatsSqlSupport {

    /** 合法 SQL 标识符: {@code col} 或 {@code alias.col} */
    private static final Pattern IDENTIFIER =
            Pattern.compile("^[A-Za-z_][A-Za-z0-9_]{0,62}(\\.[A-Za-z_][A-Za-z0-9_]{0,62})?$");

    /** 工具类禁止实例化 */
    private StatsSqlSupport() {
    }

    /**
     * 构造日期范围过滤 SQL 片段 (左闭右开, endDate 含当天整日)。
     * <p>
     * 生成形如 {@code " AND receive_time >= ? AND receive_time < ?"} 的片段,
     * 并把对应的 {@link java.sql.Date} 参数追加到 {@code args}。
     *
     * @param column 目标列名 (代码内常量, 支持 {@code alias.col} 形式)
     * @param start  起始日期 (含), 可为 null 表示不设下界
     * @param end    截止日期 (含当天), 可为 null 表示不设上界
     * @param args   JDBC 参数收集列表, 方法会向其追加绑定值 (不得为 null)
     * @return SQL 片段, 无任何过滤条件时返回空串
     * @throws BusinessException column 非法标识符, 或 args 为 null
     */
    public static String dateRangeFilter(String column, LocalDate start, LocalDate end, List<Object> args) {
        String safeColumn = requireIdentifier(column);
        if (args == null) {
            throw new BusinessException(500, "SQL 参数收集列表不得为 null");
        }
        StringBuilder sb = new StringBuilder();
        if (start != null) {
            sb.append(" AND ").append(safeColumn).append(" >= ?");
            args.add(Date.valueOf(start));
        }
        if (end != null) {
            // 右开区间: < endDate + 1 天, 等价于"包含 endDate 当天全部时刻"
            sb.append(" AND ").append(safeColumn).append(" < ?");
            args.add(Date.valueOf(end.plusDays(1)));
        }
        return sb.toString();
    }

    /**
     * 追加一个等值过滤片段 {@code " AND col = ?"} 并绑定参数 (值为 null 时不生成片段)。
     *
     * @param column 目标列名 (代码内常量)
     * @param value  绑定值, 为 null 时跳过
     * @param args   JDBC 参数收集列表 (不得为 null)
     * @return SQL 片段, 值为 null 时返回空串
     * @throws BusinessException column 非法标识符, 或 args 为 null
     */
    public static String eqFilter(String column, Object value, List<Object> args) {
        String safeColumn = requireIdentifier(column);
        if (args == null) {
            throw new BusinessException(500, "SQL 参数收集列表不得为 null");
        }
        if (value == null) {
            return "";
        }
        args.add(value);
        return " AND " + safeColumn + " = ?";
    }

    /**
     * 生成"排除测试患者数据"的 SQL 片段。
     * <p>
     * 统计口径要求只基于<b>实际业务数据</b>、排除测试数据: 凡是患者被标记为
     * {@code is_test = 1} 的标本/申请/结果一律不计入统计。通过
     * {@code NOT EXISTS} 关联 {@code lis_patient}, 形如
     * {@code " AND NOT EXISTS (SELECT 1 FROM lis_patient p WHERE p.id = <col> AND p.is_test = 1)"}。
     * 无测试患者时该片段不影响结果 (仍返回空行集语义)。
     *
     * @param patientIdColumn 当前表指向患者的列 (如 {@code s.patient_id} / {@code o.patient_id}), 代码内常量
     * @return SQL 过滤片段
     * @throws BusinessException column 非法标识符
     */
    public static String excludeTestPatients(String patientIdColumn) {
        String safe = requireIdentifier(patientIdColumn);
        return " AND NOT EXISTS (SELECT 1 FROM lis_patient p WHERE p.id = " + safe + " AND p.is_test = 1)";
    }

    /**
     * 把 {@link LocalDate} 转为 JDBC 绑定用的 {@link java.sql.Date}。
     *
     * @param date 日期, 可为 null
     * @return 对应的 {@link java.sql.Date}; 入参为 null 时返回 null
     */
    public static Date toSqlDate(LocalDate date) {
        return date == null ? null : Date.valueOf(date);
    }

    /**
     * 校验字符串是否为合法 SQL 标识符 (防御性: 杜绝任何外部输入被当作列名)。
     *
     * @param column 待校验列名
     * @return 原值
     * @throws BusinessException 非法标识符
     */
    public static String requireIdentifier(String column) {
        if (column == null || !IDENTIFIER.matcher(column).matches()) {
            throw new BusinessException(500, "非法的 SQL 列标识符: " + column);
        }
        return column;
    }
}
