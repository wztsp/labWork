package com.lis.reagent.service.impl;

import com.lis.common.core.exception.BusinessException;
import com.lis.reagent.mapper.ReagentCategoryMapper;
import com.lis.reagent.mapper.ReagentMapper;
import com.lis.reagent.mapper.ReagentPurchaseOrderMapper;
import com.lis.reagent.mapper.ReagentStockMapper;
import com.lis.reagent.mapper.ReagentStockRecordMapper;
import com.lis.reagent.service.ReagentReportService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 试剂报表服务实现（T-18 / R-11）。
 */
@Service
@RequiredArgsConstructor
public class ReagentReportServiceImpl implements ReagentReportService {

    private final ReagentPurchaseOrderMapper purchaseOrderMapper;
    private final ReagentStockRecordMapper stockRecordMapper;
    private final ReagentStockMapper stockMapper;
    private final ReagentMapper reagentMapper;
    private final ReagentCategoryMapper reagentCategoryMapper;

    @Override
    public List<Map<String, Object>> purchaseStat(LocalDate start, LocalDate end, String groupBy) {
        LocalDateTime startTime = start != null ? start.atStartOfDay() : LocalDate.now().minusMonths(1).atStartOfDay();
        LocalDateTime endTime = end != null ? end.plusDays(1).atStartOfDay() : LocalDate.now().plusDays(1).atStartOfDay();
        switch (groupBy) {
            case "supplier":
                return purchaseOrderMapper.selectPurchaseStatBySupplier(startTime, endTime);
            case "dept":
                return purchaseOrderMapper.selectPurchaseStatByDept(startTime, endTime);
            case "category":
                return purchaseOrderMapper.selectPurchaseStatByCategory(startTime, endTime);
            default:
                throw new BusinessException("不支持的采购统计维度: " + groupBy);
        }
    }

    @Override
    public List<Map<String, Object>> trend(LocalDate start, LocalDate end, String granularity) {
        LocalDate startDate = start != null ? start : LocalDate.now().minusMonths(1);
        LocalDate endDate = end != null ? end : LocalDate.now();
        LocalDateTime startTime = startDate.atStartOfDay();
        LocalDateTime endTime = endDate.plusDays(1).atStartOfDay();

        List<Map<String, Object>> raw;
        switch (granularity) {
            case "day":
                raw = stockRecordMapper.selectTrendDaily(startTime, endTime);
                break;
            case "week":
                raw = stockRecordMapper.selectTrendWeekly(startTime, endTime);
                break;
            case "month":
                raw = stockRecordMapper.selectTrendMonthly(startTime, endTime);
                break;
            default:
                throw new BusinessException("不支持的领用趋势粒度: " + granularity);
        }

        Map<String, BigDecimal> qtyByKey = new LinkedHashMap<>();
        for (Map<String, Object> row : raw) {
            Object key = row.get("period_key");
            Object qty = row.get("total_qty");
            if (key != null) {
                qtyByKey.put(key.toString(), qty != null ? toBigDecimal(qty) : BigDecimal.ZERO);
            }
        }

        List<Map<String, Object>> result = new ArrayList<>();
        for (String bucketKey : buildBuckets(startDate, endDate, granularity)) {
            Map<String, Object> point = new LinkedHashMap<>();
            point.put("period", bucketKey);
            point.put("totalQty", qtyByKey.getOrDefault(bucketKey, BigDecimal.ZERO));
            result.add(point);
        }
        return result;
    }

    @Override
    public List<Map<String, Object>> stockStructure() {
        return stockMapper.selectStockStructure();
    }

    /**
     * 按粒度生成从 start 到 end（含）的连续时间桶键，键格式与 SQL 的
     * {@code TO_CHAR(..., 'YYYY-MM-DD')} 一致。
     */
    private List<String> buildBuckets(LocalDate start, LocalDate end, String granularity) {
        List<String> keys = new ArrayList<>();
        switch (granularity) {
            case "day": {
                LocalDate d = start;
                while (!d.isAfter(end)) {
                    keys.add(d.toString());
                    d = d.plusDays(1);
                }
                break;
            }
            case "week": {
                LocalDate d = start.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
                while (!d.isAfter(end)) {
                    keys.add(d.toString());
                    d = d.plusWeeks(1);
                }
                break;
            }
            case "month": {
                LocalDate d = start.withDayOfMonth(1);
                while (!d.isAfter(end)) {
                    keys.add(d.toString());
                    d = d.plusMonths(1);
                }
                break;
            }
            default:
                throw new BusinessException("不支持的领用趋势粒度: " + granularity);
        }
        return keys;
    }

    private BigDecimal toBigDecimal(Object o) {
        if (o instanceof BigDecimal) {
            return (BigDecimal) o;
        }
        return new BigDecimal(o.toString());
    }
}