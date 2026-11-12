package com.lis.reagent.controller;

import com.lis.common.core.result.R;
import com.lis.reagent.service.ReagentStatService;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * 试剂统计报表 Controller。
 *
 * <p>统一前缀 {@code /api/reagent/stat}，金额口径见
 * {@link com.lis.reagent.mapper.ReagentStatMapper}。</p>
 */
@RestController
@RequestMapping("/api/reagent/stat")
@RequiredArgsConstructor
public class ReagentStatController {

    private final ReagentStatService statService;

    /**
     * 出入库台账（明细，按时间倒序）。
     */
    @GetMapping("/ledger")
    public R<List<Map<String, Object>>> ledger(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
            @RequestParam(required = false) Long deptId,
            @RequestParam(required = false) Long reagentId) {
        return R.ok(statService.ledger(startDate, endDate, deptId, reagentId));
    }

    /**
     * 台账四类汇总：正常入库、偿还入库、正常出库、借用出库的数量与金额。
     */
    @GetMapping("/ledger-summary")
    public R<Map<String, Object>> ledgerSummary(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
            @RequestParam(required = false) Long deptId) {
        return R.ok(statService.ledgerSummary(startDate, endDate, deptId));
    }

    /**
     * 供应商应付：已验收入库单按供应商汇总入库金额。
     */
    @GetMapping("/supplier-payable")
    public R<List<Map<String, Object>>> supplierPayable(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {
        return R.ok(statService.supplierPayable(startDate, endDate));
    }

    /**
     * 按领用小组汇总出库数量与金额。
     */
    @GetMapping("/dept-issue")
    public R<List<Map<String, Object>>> deptIssue(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {
        return R.ok(statService.deptIssue(startDate, endDate));
    }

    /**
     * 按供应商汇总发票金额、税额与张数。
     */
    @GetMapping("/invoice-summary")
    public R<List<Map<String, Object>>> invoiceSummary(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {
        return R.ok(statService.invoiceSummary(startDate, endDate));
    }

    /**
     * 按科室/小组汇总入库。
     */
    @GetMapping("/inbound-by-dept")
    public R<List<Map<String, Object>>> inboundByDept(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {
        return R.ok(statService.inboundByDept(startDate, endDate));
    }

    /**
     * 按供应商汇总入库（以入库验收日期为准）。
     */
    @GetMapping("/inbound-by-supplier")
    public R<List<Map<String, Object>>> inboundBySupplier(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {
        return R.ok(statService.inboundBySupplier(startDate, endDate));
    }

    /**
     * 按小组汇总出库（以出库单审核日期为准）。
     */
    @GetMapping("/outbound-by-dept")
    public R<List<Map<String, Object>>> outboundByDept(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {
        return R.ok(statService.outboundByDept(startDate, endDate));
    }

    /**
     * 实际消耗统计：按仪器汇总消耗数量与项目数。
     */
    @GetMapping("/consumption-stat")
    public R<List<Map<String, Object>>> consumptionStat(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
            @RequestParam(required = false) Long reagentId) {
        return R.ok(statService.consumptionStat(startDate, endDate, reagentId));
    }

    /**
     * CSV 导出（text/csv，UTF-8 BOM）。
     */
    @GetMapping("/export")
    public void export(
            @RequestParam(defaultValue = "ledger") String type,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
            @RequestParam(required = false) Long deptId,
            @RequestParam(required = false) Long reagentId,
            HttpServletResponse response) throws IOException {
        String csv = statService.exportCsv(type, startDate, endDate, deptId, reagentId);
        response.setContentType("text/csv;charset=UTF-8");
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.setHeader("Content-Disposition", "attachment; filename=reagent-stat-" + type + ".csv");
        try (OutputStream out = response.getOutputStream()) {
            out.write(csv.getBytes(StandardCharsets.UTF_8));
            out.flush();
        }
    }
}
