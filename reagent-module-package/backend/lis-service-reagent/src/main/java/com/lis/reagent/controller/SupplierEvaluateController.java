package com.lis.reagent.controller;

import com.lis.common.core.result.R;
import com.lis.reagent.service.SupplierEvaluateService;
import com.lis.reagent.vo.SupplierScoreVO;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;

/**
 * 供应商评价体系 Controller（T-16 / R-11）。
 *
 * @author LIS Team
 */
@RestController
@RequestMapping("/api/reagent/supplier-evaluate")
@RequiredArgsConstructor
public class SupplierEvaluateController {

    private final SupplierEvaluateService supplierEvaluateService;

    /** 供应商评分排行，按综合分降序 */
    @GetMapping("/rank")
    public R<List<SupplierScoreVO>> rank(@RequestParam(defaultValue = "true") boolean includeManual) {
        return R.ok(supplierEvaluateService.rank(includeManual));
    }

    /** 单个供应商评分详情 */
    @GetMapping("/{supplierId}")
    public R<SupplierScoreVO> evaluate(@PathVariable Long supplierId) {
        return R.ok(supplierEvaluateService.evaluate(supplierId));
    }

    /** 保存人工评分（写入 lis_supplier.score） */
    @PostMapping("/{supplierId}/manual")
    public R<Void> saveManualScore(@PathVariable Long supplierId,
                                   @RequestParam BigDecimal score) {
        supplierEvaluateService.saveManualScore(supplierId, score);
        return R.ok();
    }
}