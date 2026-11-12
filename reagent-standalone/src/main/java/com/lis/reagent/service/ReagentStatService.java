package com.lis.reagent.service;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * 试剂统计报表服务。
 */
public interface ReagentStatService {

    /**
     * 出入库台账（明细），按时间倒序。
     *
     * @param startDate  区间起点，可为 null（默认近一个月）
     * @param endDate    区间终点，可为 null（默认今天）
     * @param deptId     科室 ID，可为 null
     * @param reagentId  试剂 ID，可为 null
     * @return 台账行，无数据时返回空列表
     */
    List<Map<String, Object>> ledger(LocalDate startDate, LocalDate endDate, Long deptId, Long reagentId);

    /**
     * 出入库台账汇总：正常入库、偿还入库、正常出库、借用出库四类合计数量与金额。
     *
     * @param startDate 区间起点，可为 null（默认近一个月）
     * @param endDate   区间终点，可为 null（默认今天）
     * @param deptId    科室 ID，可为 null
     * @return 单行汇总，恒不为 null
     */
    Map<String, Object> ledgerSummary(LocalDate startDate, LocalDate endDate, Long deptId);

    /**
     * 供应商应付：已验收入库单按供应商汇总入库金额。
     */
    List<Map<String, Object>> supplierPayable(LocalDate startDate, LocalDate endDate);

    /**
     * 按领用小组汇总出库数量与金额。
     */
    List<Map<String, Object>> deptIssue(LocalDate startDate, LocalDate endDate);

    /**
     * 按供应商汇总发票金额、税额与张数。
     */
    List<Map<String, Object>> invoiceSummary(LocalDate startDate, LocalDate endDate);

    /**
     * 按科室/小组汇总入库数量与金额。
     */
    List<Map<String, Object>> inboundByDept(LocalDate startDate, LocalDate endDate);

    /**
     * 按供应商汇总入库数量与金额（以入库验收时间为准）。
     */
    List<Map<String, Object>> inboundBySupplier(LocalDate startDate, LocalDate endDate);

    /**
     * 按小组汇总出库数量与金额（以出库单审核时间为准）。
     */
    List<Map<String, Object>> outboundByDept(LocalDate startDate, LocalDate endDate);

    /**
     * 实际消耗统计：按仪器汇总消耗数量与项目数。
     */
    List<Map<String, Object>> consumptionStat(LocalDate startDate, LocalDate endDate, Long reagentId);

    /**
     * 导出 CSV 文本（含 UTF-8 BOM）。
     *
     * @param type      ledger / supplier-payable / dept-issue / invoice-summary / inbound-by-supplier / outbound-by-dept
     * @param startDate 区间起点，可为 null
     * @param endDate   区间终点，可为 null
     * @param deptId    科室 ID，仅台账使用，可为 null
     * @param reagentId 试剂 ID，仅台账使用，可为 null
     * @return CSV 文本
     */
    String exportCsv(String type, LocalDate startDate, LocalDate endDate, Long deptId, Long reagentId);
}
