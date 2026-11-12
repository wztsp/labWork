package com.lis.qc.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.lis.qc.entity.QCRecord;
import com.lis.qc.mapper.QCRecordMapper;
import com.lis.qc.service.QcMonthlyReportService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 质控月报服务实现
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class QcMonthlyReportServiceImpl implements QcMonthlyReportService {

    private final QCRecordMapper qcRecordMapper;
    private final JdbcTemplate jdbcTemplate;

    @Override
    public Map<String, Object> generateMonthlyReport(int year, int month) {
        YearMonth ym = YearMonth.of(year, month);
        LocalDateTime start = ym.atDay(1).atStartOfDay();
        LocalDateTime end = ym.atEndOfMonth().atTime(LocalTime.MAX);

        List<QCRecord> records = qcRecordMapper.selectList(
                new LambdaQueryWrapper<QCRecord>()
                        .ge(QCRecord::getMeasureTime, start)
                        .le(QCRecord::getMeasureTime, end));

        Map<String, Object> report = new HashMap<>();
        report.put("year", year);
        report.put("month", month);

        long total = records.size();
        long outOfControl = records.stream()
                .filter(r -> "REJECT".equalsIgnoreCase(r.getQcResult()))
                .count();
        double outOfControlRate = total == 0 ? 0.0
                : Math.round(((double) outOfControl * 100.0 / (double) total) * 100.0) / 100.0;

        report.put("totalRecords", total);
        report.put("outOfControlCount", outOfControl);
        report.put("outOfControlRate", outOfControlRate);

        // 各项目 CV 统计
        List<Map<String, Object>> itemCvList = new ArrayList<>();
        Map<Long, List<QCRecord>> byItem = records.stream()
                .filter(r -> r.getTestItemId() != null)
                .collect(Collectors.groupingBy(QCRecord::getTestItemId));
        for (Map.Entry<Long, List<QCRecord>> e : byItem.entrySet()) {
            Map<String, Object> m = new HashMap<>();
            m.put("testItemId", e.getKey());
            List<QCRecord> itemRecords = e.getValue();
            m.put("count", itemRecords.size());
            m.put("cv", calcCV(itemRecords));
            itemCvList.add(m);
        }
        report.put("itemCvStats", itemCvList);

        // 各项目合格率
        List<Map<String, Object>> itemPassRateList = new ArrayList<>();
        for (Map.Entry<Long, List<QCRecord>> e : byItem.entrySet()) {
            Map<String, Object> m = new HashMap<>();
            m.put("testItemId", e.getKey());
            List<QCRecord> itemRecords = e.getValue();
            long accepted = itemRecords.stream()
                    .filter(r -> "ACCEPT".equalsIgnoreCase(r.getQcResult()))
                    .count();
            double passRate = itemRecords.isEmpty() ? 0.0
                    : Math.round(((double) accepted * 100.0 / (double) itemRecords.size()) * 100.0) / 100.0;
            m.put("passRate", passRate);
            itemPassRateList.add(m);
        }
        report.put("itemPassRates", itemPassRateList);

        // 合并报表: 每个项目的完整信息(含名称、CV、合格率、失控数)
        List<Map<String, Object>> itemReportList = new ArrayList<>();
        // 批量查询检验指标名称
        Map<Long, String> itemNameMap = batchQueryItemNames(byItem.keySet());
        // 收集每个项目对应的仪器ID
        Map<Long, Long> itemInstrumentMap = new HashMap<>();
        for (Map.Entry<Long, List<QCRecord>> e : byItem.entrySet()) {
            for (QCRecord r : e.getValue()) {
                if (r.getInstrumentId() != null) {
                    itemInstrumentMap.put(e.getKey(), r.getInstrumentId());
                    break;
                }
            }
        }
        // 批量查询仪器名称
        Map<Long, String> instrNameMap = batchQueryInstrNames(itemInstrumentMap.values());
        for (Map.Entry<Long, List<QCRecord>> e : byItem.entrySet()) {
            Long itemId = e.getKey();
            List<QCRecord> itemRecords = e.getValue();
            long accepted = itemRecords.stream()
                    .filter(r -> "ACCEPT".equalsIgnoreCase(r.getQcResult()))
                    .count();
            long rejected = itemRecords.size() - accepted;
            double passRate = itemRecords.isEmpty() ? 0.0
                    : Math.round(((double) accepted * 100.0 / (double) itemRecords.size()) * 100.0) / 100.0;
            BigDecimal cv = calcCV(itemRecords);
            Long instrId = itemInstrumentMap.get(itemId);

            Map<String, Object> m = new HashMap<>();
            m.put("testItemId", itemId);
            m.put("itemName", itemNameMap.getOrDefault(itemId, "项目" + itemId));
            m.put("instrumentId", instrId);
            m.put("instrumentName", instrId != null ? instrNameMap.getOrDefault(instrId, "-") : "-");
            m.put("totalCount", itemRecords.size());
            m.put("outCount", rejected);
            m.put("cv", cv);
            m.put("passRate", passRate);
            itemReportList.add(m);
        }
        report.put("itemReports", itemReportList);

        // 明细记录(含检验指标名称和仪器名称)
        List<Map<String, Object>> detailList = new ArrayList<>();
        for (QCRecord r : records) {
            Map<String, Object> m = new HashMap<>();
            m.put("id", r.getId());
            m.put("testItemId", r.getTestItemId());
            m.put("itemName", itemNameMap.getOrDefault(r.getTestItemId(), "-"));
            m.put("instrumentName", r.getInstrumentId() != null
                    ? instrNameMap.getOrDefault(r.getInstrumentId(), "-") : "-");
            m.put("measureValue", r.getMeasureValue());
            m.put("measureTime", r.getMeasureTime());
            m.put("zScore", r.getZScore());
            m.put("ruleViolation", r.getRuleViolation());
            m.put("qcResult", r.getQcResult());
            detailList.add(m);
        }
        report.put("details", detailList);

        log.info("质控月报生成完成: year={}, month={}, total={}, outOfControl={}",
                year, month, total, outOfControl);
        return report;
    }

    /**
     * 计算 CV: CV = SD / 均值 * 100
     */
    private BigDecimal calcCV(List<QCRecord> records) {
        if (records == null || records.isEmpty()) return BigDecimal.ZERO;
        BigDecimal sum = BigDecimal.ZERO;
        int n = 0;
        for (QCRecord r : records) {
            if (r.getMeasureValue() != null) {
                sum = sum.add(r.getMeasureValue());
                n++;
            }
        }
        if (n == 0) return BigDecimal.ZERO;
        BigDecimal mean = sum.divide(BigDecimal.valueOf(n), 6, RoundingMode.HALF_UP);
        if (mean.compareTo(BigDecimal.ZERO) == 0) return BigDecimal.ZERO;
        BigDecimal sqSum = BigDecimal.ZERO;
        for (QCRecord r : records) {
            if (r.getMeasureValue() != null) {
                BigDecimal diff = r.getMeasureValue().subtract(mean);
                sqSum = sqSum.add(diff.multiply(diff));
            }
        }
        BigDecimal sd = BigDecimal.valueOf(Math.sqrt(
                sqSum.divide(BigDecimal.valueOf(n), 6, RoundingMode.HALF_UP).doubleValue()));
        return sd.multiply(BigDecimal.valueOf(100))
                .divide(mean.abs(), 2, RoundingMode.HALF_UP);
    }

    /** 批量查询检验指标名称 */
    private Map<Long, String> batchQueryItemNames(java.util.Set<Long> itemIds) {
        Map<Long, String> result = new HashMap<>();
        if (itemIds == null || itemIds.isEmpty()) return result;
        String inClause = itemIds.stream().map(String::valueOf).collect(Collectors.joining(","));
        jdbcTemplate.query(
                "SELECT id, item_name FROM lis_test_item WHERE id IN (" + inClause + ")",
                rs -> { result.put(rs.getLong("id"), rs.getString("item_name")); });
        return result;
    }

    /** 批量查询仪器名称 */
    private Map<Long, String> batchQueryInstrNames(java.util.Collection<Long> instrIds) {
        Map<Long, String> result = new HashMap<>();
        if (instrIds == null || instrIds.isEmpty()) return result;
        java.util.Set<Long> uniqueIds = new java.util.HashSet<>(instrIds);
        uniqueIds.removeIf(java.util.Objects::isNull);
        if (uniqueIds.isEmpty()) return result;
        String inClause = uniqueIds.stream().map(String::valueOf).collect(Collectors.joining(","));
        jdbcTemplate.query(
                "SELECT id, instrument_name FROM lis_instrument WHERE id IN (" + inClause + ")",
                rs -> { result.put(rs.getLong("id"), rs.getString("instrument_name")); });
        return result;
    }
}
