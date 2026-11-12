package com.lis.stats.service.impl;

import com.lis.stats.service.CriticalStatsService;
import com.lis.stats.support.StatsQueryGuard;
import com.lis.stats.support.StatsSqlSupport;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 危急值统计服务实现 - 基于 lis_result 表 is_critical=1 数据统计
 * <p>
 * B-8 修复: {@link JdbcTemplate} 改为构造器注入 (可 Mock); 所有入口先经
 * {@link StatsQueryGuard} 校验日期格式与区间跨度; 日期过滤统一走
 * {@link StatsSqlSupport#dateRangeFilter} 绑定强类型 {@link java.sql.Date}。
 */
@Service
public class CriticalStatsServiceImpl implements CriticalStatsService {

    private final JdbcTemplate jdbcTemplate;

    /** 统计区间跨度上限 (天), 可通过配置调整 */
    @Value("${stats.max-range-days:366}")
    private int maxRangeDays = StatsQueryGuard.DEFAULT_MAX_RANGE_DAYS;

    /**
     * @param jdbcTemplate Spring Boot 依据单一 DataSource 自动装配的 JdbcTemplate (可 Mock)
     */
    public CriticalStatsServiceImpl(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public Map<String, Object> getCriticalSummary(String startDate, String endDate) {
        LocalDate start = StatsQueryGuard.parseDate(startDate, "startDate");
        LocalDate end = StatsQueryGuard.parseDate(endDate, "endDate");
        StatsQueryGuard.checkRange(start, end, maxRangeDays);

        List<Object> args = new ArrayList<>();
        String sql = "SELECT " +
                "COUNT(*) AS total, " +
                "COUNT(CASE WHEN r.audit_status >= 1 THEN 1 END) AS confirmed, " +
                "AVG(CASE WHEN r.audit_time IS NOT NULL AND r.test_time IS NOT NULL " +
                "         THEN EXTRACT(EPOCH FROM (r.audit_time - r.test_time)) END) AS avg_confirm_seconds " +
                "FROM lis_result r LEFT JOIN lis_sample s ON r.sample_id = s.id " +
                "WHERE r.is_critical = 1" +
                StatsSqlSupport.excludeTestPatients("s.patient_id") +
                StatsSqlSupport.dateRangeFilter("r.test_time", start, end, args);
        Map<String, Object> row = jdbcTemplate.queryForMap(sql, args.toArray());
        Map<String, Object> result = new HashMap<>();
        result.put("total", row.get("total"));
        result.put("confirmed", row.get("confirmed"));
        result.put("avgConfirmSeconds", row.get("avg_confirm_seconds"));
        // 确认率 (防除零)
        long total = toLong(row.get("total"));
        long confirmed = toLong(row.get("confirmed"));
        double rate = total == 0 ? 0.0 : (confirmed * 100.0 / total);
        result.put("confirmRate", Math.round(rate * 100.0) / 100.0);
        return result;
    }

    @Override
    public List<Map<String, Object>> getCriticalByItem(String startDate, String endDate) {
        LocalDate start = StatsQueryGuard.parseDate(startDate, "startDate");
        LocalDate end = StatsQueryGuard.parseDate(endDate, "endDate");
        StatsQueryGuard.checkRange(start, end, maxRangeDays);

        List<Object> args = new ArrayList<>();
        String sql = "SELECT r.test_item_id AS test_item_id, " +
                "COALESCE(t.item_name, '未知') AS item_name, " +
                "COUNT(*) AS count, " +
                "COUNT(CASE WHEN r.audit_status >= 1 THEN 1 END) AS confirmed " +
                "FROM lis_result r LEFT JOIN lis_test_item t ON r.test_item_id = t.id " +
                "LEFT JOIN lis_sample s ON r.sample_id = s.id " +
                "WHERE r.is_critical = 1" +
                StatsSqlSupport.excludeTestPatients("s.patient_id") +
                StatsSqlSupport.dateRangeFilter("r.test_time", start, end, args) +
                " GROUP BY r.test_item_id, t.item_name ORDER BY count DESC";
        return jdbcTemplate.queryForList(sql, args.toArray());
    }

    @Override
    public List<Map<String, Object>> getCriticalByDept(String startDate, String endDate) {
        LocalDate start = StatsQueryGuard.parseDate(startDate, "startDate");
        LocalDate end = StatsQueryGuard.parseDate(endDate, "endDate");
        StatsQueryGuard.checkRange(start, end, maxRangeDays);

        List<Object> args = new ArrayList<>();
        String sql = "SELECT COALESCE(s.department, '未知') AS department, " +
                "COUNT(*) AS count, " +
                "COUNT(CASE WHEN r.audit_status >= 1 THEN 1 END) AS confirmed " +
                "FROM lis_result r LEFT JOIN lis_sample s ON r.sample_id = s.id " +
                "WHERE r.is_critical = 1" +
                StatsSqlSupport.excludeTestPatients("s.patient_id") +
                StatsSqlSupport.dateRangeFilter("r.test_time", start, end, args) +
                " GROUP BY s.department ORDER BY count DESC";
        return jdbcTemplate.queryForList(sql, args.toArray());
    }

    @Override
    public List<Map<String, Object>> getCriticalByDay(String startDate, String endDate) {
        LocalDate start = StatsQueryGuard.parseDate(startDate, "startDate");
        LocalDate end = StatsQueryGuard.parseDate(endDate, "endDate");
        StatsQueryGuard.checkRange(start, end, maxRangeDays);

        List<Object> args = new ArrayList<>();
        String sql = "SELECT DATE(r.test_time) AS day, COUNT(*) AS count, " +
                "COUNT(CASE WHEN r.audit_status >= 1 THEN 1 END) AS confirmed " +
                "FROM lis_result r LEFT JOIN lis_sample s ON r.sample_id = s.id " +
                "WHERE r.is_critical = 1" +
                StatsSqlSupport.excludeTestPatients("s.patient_id") +
                StatsSqlSupport.dateRangeFilter("r.test_time", start, end, args) +
                " GROUP BY DATE(r.test_time) ORDER BY day";
        return jdbcTemplate.queryForList(sql, args.toArray());
    }

    /**
     * 宽松转 long, null 视为 0 (防止聚合结果为 NULL 时 NPE)。
     *
     * @param val 待转换值
     * @return 对应的 long 值, 无法解析时为 0
     */
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
