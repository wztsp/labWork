package com.lis.stats.service.impl;

import com.lis.stats.service.TrendAnalysisService;
import com.lis.stats.support.StatsQueryGuard;
import com.lis.stats.support.StatsSqlSupport;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 趋势分析服务实现 - 基于 lis_result 表历史数据
 * <p>
 * B-8 修复:
 * <ol>
 *     <li>{@link JdbcTemplate} 由<b>构造器注入</b> (原为 {@code new JdbcTemplate(dataSource)} 硬 new,
 *         导致无法 Mock、无法单测)</li>
 *     <li>所有对外查询入口先经 {@link StatsQueryGuard} 校验日期格式与区间跨度, 非法入参抛
 *         {@code BusinessException} 转 400; {@code limit} 经 {@link StatsQueryGuard#clampLimit} 钳制上限</li>
 *     <li>日期过滤片段统一由 {@link StatsSqlSupport#dateRangeFilter} 生成, 绑定强类型
 *         {@link java.sql.Date}, 去掉 {@code CAST(? AS TIMESTAMP)} 字符串强转</li>
 * </ol>
 */
@Service
public class TrendAnalysisServiceImpl implements TrendAnalysisService {

    private final JdbcTemplate jdbcTemplate;

    /** 统计区间跨度上限 (天), 可通过配置调整 */
    @Value("${stats.max-range-days:366}")
    private int maxRangeDays = StatsQueryGuard.DEFAULT_MAX_RANGE_DAYS;

    /**
     * @param jdbcTemplate Spring Boot 依据单一 DataSource 自动装配的 JdbcTemplate (可 Mock)
     */
    public TrendAnalysisServiceImpl(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public List<Map<String, Object>> getPatientTrend(Long patientId, Long testItemId, int limit) {
        // B-8: limit 钳制到 [1, MAX_LIMIT], 避免 LIMIT 超大值触发全表扫描
        int safeLimit = StatsQueryGuard.clampLimit(limit);
        String sql = "SELECT r.test_time AS test_time, r.result_value AS result_value, " +
                "r.result_text AS result_text, r.unit AS unit, r.abnormal_flag AS abnormal_flag, " +
                "r.is_critical AS is_critical, r.ref_range AS ref_range " +
                "FROM lis_result r JOIN lis_sample s ON r.sample_id = s.id " +
                "WHERE s.patient_id = ? AND r.test_item_id = ? " +
                "ORDER BY r.test_time ASC LIMIT ?";
        return jdbcTemplate.queryForList(sql, patientId, testItemId, safeLimit);
    }

    @Override
    public List<Map<String, Object>> getPositiveRate(String startDate, String endDate, Long testItemId) {
        return getPositiveRate(startDate, endDate, testItemId, null);
    }

    @Override
    public List<Map<String, Object>> getPositiveRate(String startDate, String endDate, Long testItemId, String specimenType) {
        LocalDate start = StatsQueryGuard.parseDate(startDate, "startDate");
        LocalDate end = StatsQueryGuard.parseDate(endDate, "endDate");
        StatsQueryGuard.checkRange(start, end, maxRangeDays);
        // 维度值 (标本类型) 仅做长度校验, 作为 ? 参数绑定, 绝不作为列名拼接
        String dim = StatsQueryGuard.checkDim(specimenType);

        List<Object> args = new ArrayList<>();
        StringBuilder sql = new StringBuilder();
        sql.append("SELECT r.test_item_id AS test_item_id, ")
           .append("COALESCE(t.item_name, '未知') AS item_name, ")
           .append("COUNT(*) AS total, ")
           .append("COUNT(CASE WHEN r.abnormal_flag IN ('H','L') THEN 1 END) AS positive ");
        // 标本类型过滤需要关联 lis_sample
        if (dim != null) {
            sql.append(", COALESCE(s.sample_type, '未知') AS sample_type ");
        }
        sql.append("FROM lis_result r ");
        sql.append("LEFT JOIN lis_test_item t ON r.test_item_id = t.id ");
        // 关联标本以排除测试患者数据 (is_test=1)
        sql.append("LEFT JOIN lis_sample s ON r.sample_id = s.id ");
        sql.append("WHERE 1 = 1");
        sql.append(StatsSqlSupport.excludeTestPatients("s.patient_id"));
        sql.append(StatsSqlSupport.dateRangeFilter("r.test_time", start, end, args));
        if (testItemId != null) {
            sql.append(" AND r.test_item_id = ?");
            args.add(testItemId);
        }
        if (dim != null) {
            sql.append(" AND s.sample_type = ?");
            args.add(dim);
        }
        sql.append(" GROUP BY r.test_item_id, t.item_name");
        if (dim != null) {
            sql.append(", s.sample_type");
        }
        sql.append(" ORDER BY positive DESC");

        List<Map<String, Object>> rows = jdbcTemplate.queryForList(sql.toString(), args.toArray());
        // 计算阳性率
        for (Map<String, Object> row : rows) {
            long total = toLong(row.get("total"));
            long positive = toLong(row.get("positive"));
            double rate = total == 0 ? 0.0 : (positive * 100.0 / total);
            row.put("positive_rate", Math.round(rate * 100.0) / 100.0);
        }
        return rows;
    }

    @Override
    public List<Map<String, Object>> getPositiveRateTrend(String startDate, String endDate, Long testItemId, String specimenType) {
        LocalDate start = StatsQueryGuard.parseDate(startDate, "startDate");
        LocalDate end = StatsQueryGuard.parseDate(endDate, "endDate");
        StatsQueryGuard.checkRange(start, end, maxRangeDays);
        String dim = StatsQueryGuard.checkDim(specimenType);

        List<Object> args = new ArrayList<>();
        StringBuilder sql = new StringBuilder();
        sql.append("SELECT DATE(r.test_time) AS stat_date, ")
           .append("COUNT(*) AS total, ")
           .append("COUNT(CASE WHEN r.abnormal_flag IN ('H','L') THEN 1 END) AS positive ")
           .append("FROM lis_result r ");
        // 关联标本以排除测试患者数据 (is_test=1)
        sql.append("LEFT JOIN lis_sample s ON r.sample_id = s.id ");
        sql.append("WHERE 1 = 1");
        sql.append(StatsSqlSupport.excludeTestPatients("s.patient_id"));
        sql.append(StatsSqlSupport.dateRangeFilter("r.test_time", start, end, args));
        if (testItemId != null) {
            sql.append(" AND r.test_item_id = ?");
            args.add(testItemId);
        }
        if (dim != null) {
            sql.append(" AND s.sample_type = ?");
            args.add(dim);
        }
        sql.append(" GROUP BY DATE(r.test_time) ORDER BY stat_date ASC");

        List<Map<String, Object>> rows = jdbcTemplate.queryForList(sql.toString(), args.toArray());
        // 计算每日阳性率
        for (Map<String, Object> row : rows) {
            long total = toLong(row.get("total"));
            long positive = toLong(row.get("positive"));
            double rate = total == 0 ? 0.0 : (positive * 100.0 / total);
            row.put("positive_rate", Math.round(rate * 100.0) / 100.0);
        }
        return rows;
    }

    private long toLong(Object val) {
        if (val == null) {
            return 0L;
        }
        if (val instanceof Number) {
            return ((Number) val).longValue();
        }
        return Long.parseLong(val.toString());
    }
}
