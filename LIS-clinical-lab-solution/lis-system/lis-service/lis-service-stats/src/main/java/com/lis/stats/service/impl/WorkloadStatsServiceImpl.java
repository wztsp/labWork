package com.lis.stats.service.impl;

import com.lis.stats.service.WorkloadStatsService;
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
 * 工作量统计服务实现 - 基于 lis_sample 表统计
 * <p>
 * B-8 修复:
 * <ol>
 *     <li>{@link JdbcTemplate} 由<b>构造器注入</b> (原为 {@code new JdbcTemplate(dataSource)} 硬 new,
 *         导致无法 Mock、无法单测)</li>
 *     <li>所有对外查询入口先经 {@link StatsQueryGuard} 校验日期格式与区间跨度, 非法入参抛
 *         {@code BusinessException} 转 400, 不再让 PSQLException 冒泡成 500</li>
 *     <li>日期过滤片段统一由 {@link StatsSqlSupport#dateRangeFilter} 生成, 绑定强类型
 *         {@link java.sql.Date}, 去掉 {@code CAST(? AS TIMESTAMP)} 字符串强转</li>
 * </ol>
 */
@Service
public class WorkloadStatsServiceImpl implements WorkloadStatsService {

    private final JdbcTemplate jdbcTemplate;

    /** 统计区间跨度上限 (天), 可通过配置调整 */
    @Value("${stats.max-range-days:366}")
    private int maxRangeDays = StatsQueryGuard.DEFAULT_MAX_RANGE_DAYS;

    /**
     * @param jdbcTemplate Spring Boot 依据单一 DataSource 自动装配的 JdbcTemplate (可 Mock)
     */
    public WorkloadStatsServiceImpl(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public Map<String, Object> getWorkloadSummary() {
        String sql = "SELECT " +
                "COUNT(*) AS total, " +
                "COUNT(CASE WHEN s.receive_time >= CURRENT_DATE THEN 1 END) AS today, " +
                "COUNT(CASE WHEN s.is_urgent = 1 THEN 1 END) AS urgent, " +
                // 已报告 = 状态 6 (0已申请 1已采集 2已送检 3已签收 4已核收 5检测中 6已报告)
                "COUNT(CASE WHEN s.status = 6 THEN 1 END) AS reported " +
                "FROM lis_sample s WHERE s.deleted = 0" +
                StatsSqlSupport.excludeTestPatients("s.patient_id");
        Map<String, Object> row = jdbcTemplate.queryForMap(sql);
        Map<String, Object> result = new HashMap<>();
        result.put("total", row.get("total"));
        result.put("today", row.get("today"));
        result.put("urgent", row.get("urgent"));
        result.put("reported", row.get("reported"));
        return result;
    }

    @Override
    public List<Map<String, Object>> getWorkloadByDay(String startDate, String endDate) {
        LocalDate start = StatsQueryGuard.parseDate(startDate, "startDate");
        LocalDate end = StatsQueryGuard.parseDate(endDate, "endDate");
        StatsQueryGuard.checkRange(start, end, maxRangeDays);

        List<Object> args = new ArrayList<>();
        String sql = "SELECT TO_CHAR(s.receive_time, 'YYYY-MM-DD') AS day, COUNT(*) AS count " +
                "FROM lis_sample s WHERE s.deleted = 0 AND s.receive_time IS NOT NULL" +
                StatsSqlSupport.excludeTestPatients("s.patient_id") +
                StatsSqlSupport.dateRangeFilter("s.receive_time", start, end, args) +
                " GROUP BY TO_CHAR(s.receive_time, 'YYYY-MM-DD') ORDER BY day";
        return jdbcTemplate.queryForList(sql, args.toArray());
    }

    @Override
    public List<Map<String, Object>> getWorkloadByDept(String startDate, String endDate) {
        LocalDate start = StatsQueryGuard.parseDate(startDate, "startDate");
        LocalDate end = StatsQueryGuard.parseDate(endDate, "endDate");
        StatsQueryGuard.checkRange(start, end, maxRangeDays);

        List<Object> args = new ArrayList<>();
        String sql = "SELECT COALESCE(s.department, '未知') AS department, COUNT(*) AS count " +
                "FROM lis_sample s WHERE s.deleted = 0" +
                StatsSqlSupport.excludeTestPatients("s.patient_id") +
                StatsSqlSupport.dateRangeFilter("s.receive_time", start, end, args) +
                " GROUP BY s.department ORDER BY count DESC";
        return jdbcTemplate.queryForList(sql, args.toArray());
    }

    @Override
    public List<Map<String, Object>> getWorkloadByInstrument(String startDate, String endDate) {
        LocalDate start = StatsQueryGuard.parseDate(startDate, "startDate");
        LocalDate end = StatsQueryGuard.parseDate(endDate, "endDate");
        StatsQueryGuard.checkRange(start, end, maxRangeDays);

        List<Object> args = new ArrayList<>();
        String sql = "SELECT COALESCE(i.instrument_name, '未指定') AS instrument_name, " +
                "COUNT(DISTINCT r.sample_id) AS count " +
                "FROM lis_result r LEFT JOIN lis_instrument i ON r.instrument_id = i.id " +
                "LEFT JOIN lis_sample s ON r.sample_id = s.id " +
                "WHERE 1 = 1" +
                StatsSqlSupport.excludeTestPatients("s.patient_id") +
                StatsSqlSupport.dateRangeFilter("r.test_time", start, end, args) +
                " GROUP BY i.instrument_name ORDER BY count DESC";
        return jdbcTemplate.queryForList(sql, args.toArray());
    }
}
