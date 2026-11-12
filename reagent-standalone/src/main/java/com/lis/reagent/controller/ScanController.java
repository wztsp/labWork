package com.lis.reagent.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.lis.common.core.exception.BusinessException;
import com.lis.common.core.result.R;
import com.lis.reagent.entity.ReagentStock;
import com.lis.reagent.entity.ReagentStockRecord;
import com.lis.reagent.service.ReagentStockService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 试剂扫码 Controller
 * <p>
 * 扫码入库: 扫描条码 → 匹配试剂 → 确认入库
 * 扫码出库: 扫描条码 → 匹配库存 → 按批次出库 (FIFO)
 */
@Slf4j
@RestController
@RequestMapping("/api/reagent/scan")
@RequiredArgsConstructor
public class ScanController {

    private final ReagentStockService stockService;

    /**
     * 扫码入库
     * body: { barcode, reagentId, lotNo, quantity, expiryDate, location, operatorId, operatorName }
     */
    @PostMapping("/in")
    public R<ReagentStock> scanIn(@RequestBody Map<String, Object> body) {
        String barcode = (String) body.get("barcode");
        Long reagentId = parseLong(body.get("reagentId"));
        if (barcode == null || barcode.isBlank()) {
            throw new BusinessException("条码不能为空");
        }
        if (reagentId == null) {
            throw new BusinessException("试剂ID不能为空");
        }
        BigDecimal quantity = parseBigDecimal(body.get("quantity"));
        if (quantity == null || quantity.compareTo(BigDecimal.ZERO) <= 0) {
            throw new BusinessException("入库数量必须大于0");
        }
        ReagentStock stock = new ReagentStock();
        stock.setReagentId(reagentId);
        stock.setLotNo((String) body.get("lotNo"));
        stock.setBarcode(barcode);
        stock.setLocation((String) body.get("location"));
        Object expiry = body.get("expiryDate");
        if (expiry != null && !expiry.toString().isEmpty()) {
            stock.setExpiryDate(LocalDate.parse(expiry.toString()));
        }
        Long operatorId = parseLong(body.get("operatorId"));
        String operatorName = (String) body.get("operatorName");
        ReagentStock result = stockService.inStock(stock, quantity, operatorId, operatorName,
                "SCAN-IN-" + barcode, "扫码入库");
        log.info("扫码入库: barcode={}, reagentId={}, qty={}", barcode, reagentId, quantity);
        return R.ok(result);
    }

    /**
     * 扫码出库
     * body: { barcode, quantity, operatorId, operatorName, sourceNo, remark }
     * 优先按条码匹配批次出库, 找不到则对该试剂 FIFO 出库
     */
    @PostMapping("/out")
    public R<Map<String, Object>> scanOut(@RequestBody Map<String, Object> body) {
        String barcode = (String) body.get("barcode");
        if (barcode == null || barcode.isBlank()) {
            throw new BusinessException("条码不能为空");
        }
        BigDecimal quantity = parseBigDecimal(body.get("quantity"));
        if (quantity == null || quantity.compareTo(BigDecimal.ZERO) <= 0) {
            throw new BusinessException("出库数量必须大于0");
        }
        Long operatorId = parseLong(body.get("operatorId"));
        String operatorName = (String) body.get("operatorName");
        String sourceNo = (String) body.get("sourceNo");
        String remark = body.get("remark") == null ? "扫码出库" : (String) body.get("remark");

        // 按条码匹配库存批次
        ReagentStock stock = stockService.getOne(new LambdaQueryWrapper<ReagentStock>()
                .eq(ReagentStock::getBarcode, barcode)
                .last("LIMIT 1"));
        Map<String, Object> result = new HashMap<>();
        if (stock != null) {
            ReagentStockRecord record = stockService.outStock(stock.getId(), quantity, operatorId,
                    operatorName, sourceNo != null ? sourceNo : "SCAN-OUT-" + barcode, remark);
            result.put("mode", "BATCH");
            result.put("records", List.of(record));
            log.info("扫码出库(批次): barcode={}, stockId={}, qty={}", barcode, stock.getId(), quantity);
        } else {
            // 未匹配到条码, 退化为按试剂 FIFO 出库 (需提供 reagentId 或通过 lotNo 反查)
            Long reagentId = parseLong(body.get("reagentId"));
            if (reagentId == null) {
                throw new BusinessException("条码 [" + barcode + "] 未匹配到库存, 且未提供 reagentId 无法 FIFO 出库");
            }
            List<ReagentStockRecord> records = stockService.outStockFifo(reagentId, quantity, operatorId,
                    operatorName, sourceNo != null ? sourceNo : "SCAN-OUT-" + barcode, remark);
            result.put("mode", "FIFO");
            result.put("records", records);
            log.info("扫码出库(FIFO): barcode={}, reagentId={}, qty={}", barcode, reagentId, quantity);
        }
        return R.ok(result);
    }

    /**
     * 扫码查询: 扫描条码 → 返回匹配的库存信息 (入库/出库前确认)
     */
    @GetMapping("/query")
    public R<ReagentStock> query(@RequestParam String barcode) {
        ReagentStock stock = stockService.getOne(new LambdaQueryWrapper<ReagentStock>()
                .eq(ReagentStock::getBarcode, barcode)
                .last("LIMIT 1"));
        return R.ok(stock);
    }

    private BigDecimal parseBigDecimal(Object val) {
        if (val == null) return null;
        try {
            return new BigDecimal(val.toString());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /**
     * 兼容前端 JSON 中 Long 字段以 Number 或 String 两种形式传入（防止 Long→String 序列化后回传仍是字符串）。
     */
    private Long parseLong(Object val) {
        if (val == null) return null;
        try {
            if (val instanceof Number) {
                return ((Number) val).longValue();
            }
            String s = val.toString().trim();
            if (s.isEmpty()) return null;
            return Long.parseLong(s);
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
