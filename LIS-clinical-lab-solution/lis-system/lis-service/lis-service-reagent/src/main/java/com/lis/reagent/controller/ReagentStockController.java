package com.lis.reagent.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.lis.common.core.result.PageResult;
import com.lis.common.core.result.R;
import com.lis.reagent.entity.ReagentStock;
import com.lis.reagent.entity.ReagentStockRecord;
import com.lis.reagent.service.ReagentStockService;
import com.lis.reagent.vo.LowStockAlertDTO;
import com.lis.reagent.vo.OverStockAlertDTO;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;

/**
 * 试剂库存 Controller。
 *
 * <p><b>本类不提供库存的直接增删改端点。</b>库存数量只允许由业务流程产生
 * —— 入库走 {@code POST /in}、出库走 {@code POST /out/**}、盘点调账走
 * {@code /api/reagent/stock-check/{id}/submit}。三者都会同步写出
 * {@code lis_reagent_stock_record} 流水，保证「账实相符 + 可追溯」。</p>
 *
 * <p>原先的 {@code POST /}、{@code PUT /{id}}、{@code DELETE /{id}} 三个裸端点
 * 可以绕过流水直接改数字，等于给账目开后门，已按主理人 Q11 裁决<b>删除</b>
 * （不是加权限——加权限只是把后门锁上，门还在）。</p>
 */
@RestController
@RequestMapping("/api/reagent/stock")
@RequiredArgsConstructor
public class ReagentStockController {

    private final ReagentStockService stockService;

    @GetMapping
    public R<PageResult<ReagentStock>> page(@RequestParam(defaultValue = "1") long current,
                                            @RequestParam(defaultValue = "10") long size,
                                            @RequestParam(required = false) String reagentName,
                                            @RequestParam(required = false) String batchNo,
                                            @RequestParam(required = false) String barcode,
                                            @RequestParam(required = false) Integer status,
                                            @RequestParam(required = false) String location) {
        Page<ReagentStock> page = stockService.queryPage(current, size, reagentName, batchNo, barcode, status, location);
        return R.ok(new PageResult<>(page.getRecords(), page.getTotal(), page.getCurrent(), page.getSize()));
    }

    /** 入库 */
    @PostMapping("/in")
    public R<ReagentStock> inStock(@RequestBody ReagentStock stock,
                                   @RequestParam(required = false) BigDecimal quantity,
                                   @RequestParam(required = false) Long operatorId,
                                   @RequestParam(required = false) String operatorName,
                                   @RequestParam(required = false) String sourceNo,
                                   @RequestParam(required = false) String remark) {
        if (quantity == null) quantity = stock.getQuantity();
        return R.ok(stockService.inStock(stock, quantity, operatorId, operatorName, sourceNo, remark));
    }

    /** FIFO 出库 */
    @PostMapping("/out/fifo")
    public R<List<ReagentStockRecord>> outStockFifo(@RequestParam Long reagentId,
                                                    @RequestParam BigDecimal quantity,
                                                    @RequestParam(required = false) Long operatorId,
                                                    @RequestParam(required = false) String operatorName,
                                                    @RequestParam(required = false) String sourceNo,
                                                    @RequestParam(required = false) String remark) {
        return R.ok(stockService.outStockFifo(reagentId, quantity, operatorId, operatorName, sourceNo, remark));
    }

    /** 指定批次出库 */
    @PostMapping("/out/{stockId}")
    public R<ReagentStockRecord> outStock(@PathVariable Long stockId,
                                          @RequestParam BigDecimal quantity,
                                          @RequestParam(required = false) Long operatorId,
                                          @RequestParam(required = false) String operatorName,
                                          @RequestParam(required = false) String sourceNo,
                                          @RequestParam(required = false) String remark) {
        return R.ok(stockService.outStock(stockId, quantity, operatorId, operatorName, sourceNo, remark));
    }

    /** 近效期预警 (30天) */
    @GetMapping("/near-expiry")
    public R<List<ReagentStock>> nearExpiry(@RequestParam(defaultValue = "30") int days) {
        return R.ok(stockService.listNearExpiry(days));
    }

    /** 安全库存预警 */
    @GetMapping("/below-safety")
    public R<List<ReagentStock>> belowSafety() {
        return R.ok(stockService.listBelowSafetyStock());
    }

    /** 低库存预警（聚合 SQL，缺陷 I 修复，推荐使用） */
    @GetMapping("/low-stock")
    public R<List<LowStockAlertDTO>> lowStock() {
        return R.ok(stockService.listLowStock());
    }

    /** 高库存预警（按试剂汇总可用库存，超过库存上限） */
    @GetMapping("/over-stock")
    public R<List<OverStockAlertDTO>> overStock() {
        return R.ok(stockService.listOverStock());
    }

    /** 批次列表（不聚合，默认包含 quantity=0 的批次） */
    @GetMapping("/batches")
    public R<PageResult<ReagentStock>> batches(@RequestParam(defaultValue = "1") long current,
                                               @RequestParam(defaultValue = "10") long size,
                                               @RequestParam(required = false) Long reagentId,
                                               @RequestParam(required = false) String batchNo,
                                               @RequestParam(required = false) Integer status,
                                               @RequestParam(defaultValue = "true") boolean includeZero) {
        Page<ReagentStock> page = stockService.queryBatchPage(current, size, reagentId, batchNo, status, includeZero);
        return R.ok(new PageResult<>(page.getRecords(), page.getTotal(), page.getCurrent(), page.getSize()));
    }

    /** 锁定过期批次 */
    @PostMapping("/lock-expired")
    public R<Integer> lockExpired() {
        return R.ok(stockService.lockExpiredStock());
    }
}
