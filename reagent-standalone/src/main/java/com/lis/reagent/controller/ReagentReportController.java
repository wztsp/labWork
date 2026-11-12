package com.lis.reagent.controller;

import com.lis.common.core.result.R;
import com.lis.reagent.service.ReagentReportService;
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
 * 试剂报表 Controller（T-18 / R-11）。
 */
@RestController
@RequestMapping("/api/reagent/report")
@RequiredArgsConstructor
public class ReagentReportController {

    private final ReagentReportService reportService;

    /**
     * 采购统计。
     *
     * @param start   区间起点，可为 null（默认近一个月）
     * @param end     区间终点，可为 null（默认今天）
     * @param groupBy supplier / dept / category
     * @return 分组统计行
     */
    @GetMapping("/purchase-stat")
    public R<List<Map<String, Object>>> purchaseStat(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate start,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate end,
            @RequestParam(defaultValue = "supplier") String groupBy) {
        return R.ok(reportService.purchaseStat(start, end, groupBy));
    }

    /**
     * 领用趋势时间序列。
     *
     * @param start       区间起点，可为 null（默认近一个月）
     * @param end         区间终点，可为 null（默认今天）
     * @param granularity day / week / month
     * @return 时间序列点列表
     */
    @GetMapping("/trend")
    public R<List<Map<String, Object>>> trend(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate start,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate end,
            @RequestParam(defaultValue = "day") String granularity) {
        return R.ok(reportService.trend(start, end, granularity));
    }

    /**
     * 库存结构分析。
     *
     * @return 各分类库存结构行
     */
    @GetMapping("/stock-structure")
    public R<List<Map<String, Object>>> stockStructure() {
        return R.ok(reportService.stockStructure());
    }

    /**
     * 采购统计 Excel 导出（制表符分隔的文本格式，无 POI 依赖）。
     *
     * @param start   区间起点，可为 null（默认近一个月）
     * @param end     区间终点，可为 null（默认今天）
     * @param groupBy supplier / dept / category
     * @param response HTTP 响应，用于输出文件内容
     */
    @GetMapping("/export/purchase-stat")
    public void exportPurchaseStat(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate start,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate end,
            @RequestParam(defaultValue = "supplier") String groupBy,
            HttpServletResponse response) throws IOException {
        List<Map<String, Object>> rows = reportService.purchaseStat(start, end, groupBy);

        String dimHeader;
        switch (groupBy) {
            case "dept":
                dimHeader = "科室";
                break;
            case "category":
                dimHeader = "分类";
                break;
            default:
                dimHeader = "供应商";
                break;
        }

        StringBuilder sb = new StringBuilder();
        // UTF-8 BOM，便于 Excel 正确识别中文编码
        sb.append('\uFEFF');
        sb.append(dimHeader).append('\t').append("订单数\t总金额\t到货订单数\r\n");
        for (Map<String, Object> row : rows) {
            String dimValue;
            switch (groupBy) {
                case "dept":
                    dimValue = toText(row.get("dept_name"));
                    break;
                case "category":
                    dimValue = toText(row.get("category_name"));
                    break;
                default:
                    dimValue = toText(row.get("supplier_name"));
                    break;
            }
            sb.append(dimValue).append('\t')
                    .append(toText(row.get("order_count"))).append('\t')
                    .append(toText(row.get("total_amount"))).append('\t')
                    .append(toText(row.get("arrived_order_count"))).append("\r\n");
        }

        response.setContentType("application/vnd.ms-excel;charset=UTF-8");
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.setHeader("Content-Disposition", "attachment; filename=purchase-stat.xls");
        byte[] bytes = sb.toString().getBytes(StandardCharsets.UTF_8);
        try (OutputStream out = response.getOutputStream()) {
            out.write(bytes);
            out.flush();
        }
    }

    private String toText(Object o) {
        return o == null ? "" : String.valueOf(o);
    }
}