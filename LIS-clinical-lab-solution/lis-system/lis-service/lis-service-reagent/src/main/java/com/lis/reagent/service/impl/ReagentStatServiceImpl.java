package com.lis.reagent.service.impl;

import com.lis.common.core.exception.BusinessException;
import com.lis.reagent.mapper.ReagentStatMapper;
import com.lis.reagent.service.ReagentStatService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 试剂统计报表服务实现。
 *
 * <p>日期参数缺省口径与既有报表保持一致：起点默认近一个月，终点默认今天，
 * 时间区间统一按 {@code [start 00:00, end+1 00:00)} 传递，避免边界漏数据。</p>
 */
@Service
@RequiredArgsConstructor
public class ReagentStatServiceImpl implements ReagentStatService {

    private static final DateTimeFormatter DATE_TIME_FORMATTER =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final ReagentStatMapper statMapper;

    @Override
    public List<Map<String, Object>> ledger(LocalDate startDate, LocalDate endDate, Long deptId, Long reagentId) {
        return statMapper.selectLedger(startOf(startDate), endOf(endDate), deptId, reagentId);
    }

    @Override
    public Map<String, Object> ledgerSummary(LocalDate startDate, LocalDate endDate, Long deptId) {
        Map<String, Object> row = statMapper.selectLedgerSummary(startOf(startDate), endOf(endDate), deptId);
        return row != null ? row : new LinkedHashMap<>();
    }

    @Override
    public List<Map<String, Object>> supplierPayable(LocalDate startDate, LocalDate endDate) {
        return statMapper.selectSupplierPayable(startOf(startDate), endOf(endDate));
    }

    @Override
    public List<Map<String, Object>> deptIssue(LocalDate startDate, LocalDate endDate) {
        return statMapper.selectDeptIssue(startOf(startDate), endOf(endDate));
    }

    @Override
    public List<Map<String, Object>> invoiceSummary(LocalDate startDate, LocalDate endDate) {
        return statMapper.selectInvoiceSummary(startDay(startDate), endDay(endDate));
    }

    @Override
    public List<Map<String, Object>> inboundByDept(LocalDate startDate, LocalDate endDate) {
        return statMapper.selectInboundByDept(startOf(startDate), endOf(endDate));
    }

    @Override
    public List<Map<String, Object>> inboundBySupplier(LocalDate startDate, LocalDate endDate) {
        return statMapper.selectInboundBySupplier(startOf(startDate), endOf(endDate));
    }

    @Override
    public List<Map<String, Object>> outboundByDept(LocalDate startDate, LocalDate endDate) {
        return statMapper.selectOutboundByDept(startOf(startDate), endOf(endDate));
    }

    @Override
    public List<Map<String, Object>> consumptionStat(LocalDate startDate, LocalDate endDate, Long reagentId) {
        return statMapper.selectConsumptionStat(startOf(startDate), endOf(endDate), reagentId);
    }

    @Override
    public String exportCsv(String type, LocalDate startDate, LocalDate endDate, Long deptId, Long reagentId) {
        String exportType = type == null || type.trim().isEmpty() ? "ledger" : type.trim().toLowerCase();
        switch (exportType) {
            case "ledger":
                return buildCsv(
                        List.of("时间", "试剂编码", "试剂名称", "类型", "数量", "单价", "金额", "科室", "操作人", "来源单号", "备注"),
                        ledger(startDate, endDate, deptId, reagentId),
                        List.of("createTime", "reagentCode", "reagentName", "recordTypeName", "quantity",
                                "unitPrice", "amount", "deptName", "operatorName", "sourceNo", "remark"));
            case "supplier-payable":
                return buildCsv(
                        List.of("供应商", "入库单数", "入库金额"),
                        supplierPayable(startDate, endDate),
                        List.of("supplierName", "inboundCount", "totalAmount"));
            case "dept-issue":
                return buildCsv(
                        List.of("科室", "出库数量", "出库金额", "流水条数"),
                        deptIssue(startDate, endDate),
                        List.of("deptName", "totalQty", "totalAmount", "recordCount"));
            case "invoice-summary":
                return buildCsv(
                        List.of("供应商", "发票张数", "价税合计", "税额"),
                        invoiceSummary(startDate, endDate),
                        List.of("supplierName", "invoiceCount", "totalAmount", "totalTaxAmount"));
            case "inbound-by-supplier":
                return buildCsv(
                        List.of("供应商", "入库单数", "入库数量", "入库金额"),
                        inboundBySupplier(startDate, endDate),
                        List.of("supplierName", "inboundCount", "totalQty", "totalAmount"));
            case "outbound-by-dept":
                return buildCsv(
                        List.of("科室", "出库单数", "出库数量", "出库金额"),
                        outboundByDept(startDate, endDate),
                        List.of("deptName", "requisitionCount", "totalQty", "totalAmount"));
            default:
                throw new BusinessException("不支持的导出类型: " + type);
        }
    }

    /** 起点默认近一个月。 */
    private LocalDate startDay(LocalDate startDate) {
        return startDate != null ? startDate : LocalDate.now().minusMonths(1);
    }

    /** 终点默认今天（含）。 */
    private LocalDate endDay(LocalDate endDate) {
        return endDate != null ? endDate : LocalDate.now();
    }

    /** 时间区间起点：当天 00:00。 */
    private LocalDateTime startOf(LocalDate startDate) {
        return startDay(startDate).atStartOfDay();
    }

    /** 时间区间终点（不含）：终点次日 00:00。 */
    private LocalDateTime endOf(LocalDate endDate) {
        return endDay(endDate).plusDays(1).atStartOfDay();
    }

    /** 组装 CSV 文本，前置 UTF-8 BOM。 */
    private String buildCsv(List<String> headers, List<Map<String, Object>> rows, List<String> keys) {
        StringBuilder sb = new StringBuilder();
        sb.append('\uFEFF');
        sb.append(csvRow(headers)).append("\r\n");
        if (rows != null) {
            for (Map<String, Object> row : rows) {
                List<String> cells = new java.util.ArrayList<>(keys.size());
                for (String key : keys) {
                    cells.add(formatValue(row.get(key)));
                }
                sb.append(csvRow(cells)).append("\r\n");
            }
        }
        return sb.toString();
    }

    /** 单行 CSV：所有字段统一加引号并转义，避免逗号/换行破坏列结构。 */
    private String csvRow(List<String> cells) {
        StringBuilder line = new StringBuilder();
        for (int i = 0; i < cells.size(); i++) {
            if (i > 0) {
                line.append(',');
            }
            line.append('"').append(cells.get(i).replace("\"", "\"\"")).append('"');
        }
        return line.toString();
    }

    /** 时间/日期格式化，其余按字符串输出，null 输出空串。 */
    private String formatValue(Object value) {
        if (value == null) {
            return "";
        }
        if (value instanceof LocalDateTime) {
            return ((LocalDateTime) value).format(DATE_TIME_FORMATTER);
        }
        if (value instanceof Timestamp) {
            return ((Timestamp) value).toLocalDateTime().format(DATE_TIME_FORMATTER);
        }
        if (value instanceof LocalDate) {
            return value.toString();
        }
        return String.valueOf(value);
    }
}
