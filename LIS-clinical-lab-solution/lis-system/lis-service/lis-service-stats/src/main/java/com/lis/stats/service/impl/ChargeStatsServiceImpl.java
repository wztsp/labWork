package com.lis.stats.service.impl;

import com.lis.stats.service.ChargeStatsService;
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
 * 收费统计服务实现 - 基于 lis_order_item 表 fee 字段统计
 * <p>
 * B-8 修复: {@link JdbcTemplate} 改为构造器注入 (可 Mock); 所有入口先经
 * {@link StatsQueryGuard} 校验日期格式与区间跨度; 日期过滤统一走
 * {@link StatsSqlSupport#dateRangeFilter} 绑定强类型 {@link java.sql.Date}。
 */
@Service
public class ChargeStatsServiceImpl implements ChargeStatsService {

    private final JdbcTemplate jdbcTemplate;

    /** 统计区间跨度上限 (天), 可通过配置调整 */
    @Value("${stats.max-range-days:366}")
    private int maxRangeDays = StatsQueryGuard.DEFAULT_MAX_RANGE_DAYS;

    /**
     * @param jdbcTemplate Spring Boot 依据单一 DataSource 自动装配的 JdbcTemplate (可 Mock)
     */
    public ChargeStatsServiceImpl(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public Map<String, Object> getChargeSummary(String startDate, String endDate, String department) {
        LocalDate start = StatsQueryGuard.parseDate(startDate, "startDate");
        LocalDate end = StatsQueryGuard.parseDate(endDate, "endDate");
        StatsQueryGuard.checkRange(start, end, maxRangeDays);
        String dept = StatsQueryGuard.checkDim(department);

        List<Object> args = new ArrayList<>();
        String sql = "SELECT " +
                "SUM(CASE WHEN o.apply_time >= CURRENT_DATE THEN oi.fee ELSE 0 END) AS today_fee, " +
                "SUM(oi.fee) AS total_fee, " +
                "COUNT(*) AS item_count, " +
                "COUNT(DISTINCT oi.order_id) AS order_count " +
                "FROM lis_order_item oi JOIN lis_lab_order o ON oi.order_id = o.id WHERE 1 = 1" +
                StatsSqlSupport.excludeTestPatients("o.patient_id") +
                StatsSqlSupport.eqFilter("o.apply_dept", dept, args) +
                StatsSqlSupport.dateRangeFilter("o.apply_time", start, end, args);
        return jdbcTemplate.queryForMap(sql, args.toArray());
    }

    @Override
    public List<Map<String, Object>> getChargeByDay(String startDate, String endDate, String department) {
        LocalDate start = StatsQueryGuard.parseDate(startDate, "startDate");
        LocalDate end = StatsQueryGuard.parseDate(endDate, "endDate");
        StatsQueryGuard.checkRange(start, end, maxRangeDays);
        String dept = StatsQueryGuard.checkDim(department);

        List<Object> args = new ArrayList<>();
        String sql = "SELECT DATE(o.apply_time) AS day, SUM(oi.fee) AS total_fee, COUNT(*) AS count " +
                "FROM lis_order_item oi JOIN lis_lab_order o ON oi.order_id = o.id WHERE 1 = 1" +
                StatsSqlSupport.excludeTestPatients("o.patient_id") +
                StatsSqlSupport.eqFilter("o.apply_dept", dept, args) +
                StatsSqlSupport.dateRangeFilter("o.apply_time", start, end, args) +
                " GROUP BY DATE(o.apply_time) ORDER BY day";
        return jdbcTemplate.queryForList(sql, args.toArray());
    }

    @Override
    public List<Map<String, Object>> getChargeByDept(String startDate, String endDate) {
        LocalDate start = StatsQueryGuard.parseDate(startDate, "startDate");
        LocalDate end = StatsQueryGuard.parseDate(endDate, "endDate");
        StatsQueryGuard.checkRange(start, end, maxRangeDays);

        List<Object> args = new ArrayList<>();
        String sql = "SELECT COALESCE(o.apply_dept, '未知') AS department, " +
                "SUM(oi.fee) AS total_fee, " +
                "COUNT(*) AS item_count, " +
                "COUNT(DISTINCT oi.order_id) AS order_count " +
                "FROM lis_order_item oi JOIN lis_lab_order o ON oi.order_id = o.id WHERE 1 = 1" +
                StatsSqlSupport.excludeTestPatients("o.patient_id") +
                StatsSqlSupport.dateRangeFilter("o.apply_time", start, end, args) +
                " GROUP BY o.apply_dept ORDER BY total_fee DESC";
        return jdbcTemplate.queryForList(sql, args.toArray());
    }

    @Override
    public List<Map<String, Object>> getChargeByItem(String startDate, String endDate, String department) {
        LocalDate start = StatsQueryGuard.parseDate(startDate, "startDate");
        LocalDate end = StatsQueryGuard.parseDate(endDate, "endDate");
        StatsQueryGuard.checkRange(start, end, maxRangeDays);
        String dept = StatsQueryGuard.checkDim(department);

        List<Object> args = new ArrayList<>();
        String sql = "SELECT oi.test_item_id AS test_item_id, " +
                "COALESCE(oi.test_item_name, '未知') AS item_name, " +
                "SUM(oi.fee) AS total_fee, " +
                "COUNT(*) AS count " +
                "FROM lis_order_item oi JOIN lis_lab_order o ON oi.order_id = o.id WHERE 1 = 1" +
                StatsSqlSupport.excludeTestPatients("o.patient_id") +
                StatsSqlSupport.eqFilter("o.apply_dept", dept, args) +
                StatsSqlSupport.dateRangeFilter("o.apply_time", start, end, args) +
                " GROUP BY oi.test_item_id, oi.test_item_name ORDER BY total_fee DESC";
        return jdbcTemplate.queryForList(sql, args.toArray());
    }
}
