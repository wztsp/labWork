package com.lis.reagent.service;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * 试剂报表服务（T-18 / R-11）。
 *
 * <p>提供采购统计、领用趋势、库存结构三类报表聚合查询，供报表与 Excel 导出复用。</p>
 */
public interface ReagentReportService {

    /**
     * 采购统计（基于 {@code lis_reagent_purchase_order}）。
     *
     * @param start   区间起点，可为 null（默认近一个月）
     * @param end     区间终点，可为 null（默认今天）
     * @param groupBy 分组维度：supplier / dept / category
     * @return 分组统计行，无数据时返回空列表
     */
    List<Map<String, Object>> purchaseStat(LocalDate start, LocalDate end, String groupBy);

    /**
     * 领用趋势（基于 {@code lis_reagent_stock_record}，record_type=2 领用出库）。
     *
     * <p>按日/周/月生成连续时间序列，空档补 0。</p>
     *
     * @param start       区间起点，可为 null（默认近一个月）
     * @param end         区间终点，可为 null（默认今天）
     * @param granularity 粒度：day / week / month
     * @return 时间序列点列表，恒不为 null
     */
    List<Map<String, Object>> trend(LocalDate start, LocalDate end, String granularity);

    /**
     * 库存结构分析（基于 {@code lis_reagent_stock} 按分类分布）。
     *
     * @return 各分类库存结构行，无数据时返回空列表
     */
    List<Map<String, Object>> stockStructure();
}