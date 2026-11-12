package com.lis.sample.controller;

import com.lis.common.core.result.R;
import com.lis.sample.entity.BarcodeRule;
import com.lis.sample.service.BarcodeService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 条码 Controller
 * 提供条码生成 + 规则管理接口
 */
@RestController
@RequestMapping("/api/sample/barcode")
@RequiredArgsConstructor
public class BarcodeController {

    private final BarcodeService barcodeService;

    /**
     * 生成条码 (默认使用 SAMPLE 规则)
     */
    @PostMapping("/generate")
    public R<String> generate() {
        return R.ok(barcodeService.generateBarcode());
    }

    /**
     * 按规则编码生成条码
     * @param ruleCode 规则编码 (SAMPLE/ORDER/VISIT/PATIENT)
     */
    @PostMapping("/generate/{ruleCode}")
    public R<String> generateByRule(@PathVariable String ruleCode) {
        return R.ok(barcodeService.generateBarcode(ruleCode));
    }

    /**
     * 校验条码唯一性
     */
    @GetMapping("/validate/{barcode}")
    public R<Boolean> validate(@PathVariable String barcode) {
        return R.ok(barcodeService.validateBarcode(barcode));
    }

    // ==================== 规则管理 ====================

    /**
     * 查询所有条码规则
     */
    @GetMapping("/rules")
    public R<List<BarcodeRule>> listRules() {
        return R.ok(barcodeService.listRules());
    }

    /**
     * 查询单个规则详情
     */
    @GetMapping("/rules/{ruleCode}")
    public R<BarcodeRule> getRule(@PathVariable String ruleCode) {
        return R.ok(barcodeService.getEnabledRule(ruleCode));
    }

    /**
     * 更新条码规则
     * 可更新字段: ruleName, prefix, datePattern, seqLength, seqStrategy, remark
     */
    @PutMapping("/rules")
    public R<Void> updateRule(@RequestBody BarcodeRule rule) {
        barcodeService.updateRule(rule);
        return R.ok();
    }
}
