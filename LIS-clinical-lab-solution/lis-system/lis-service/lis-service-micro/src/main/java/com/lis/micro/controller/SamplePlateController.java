package com.lis.micro.controller;

import com.lis.common.core.result.R;
import com.lis.micro.dto.GeneratePlateRequest;
import com.lis.micro.entity.SamplePlate;
import com.lis.micro.service.SamplePlateService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 标本培养皿条码 Controller
 */
@RestController
@RequestMapping("/api/micro/sample-plate")
@RequiredArgsConstructor
public class SamplePlateController {

    private final SamplePlateService samplePlateService;

    /** 生成培养皿条码 (优先按 planId, 其次按标本类型+检验项目匹配) */
    @PostMapping("/generate")
    public R<List<SamplePlate>> generate(@RequestBody GeneratePlateRequest req) {
        return R.ok(samplePlateService.generatePlates(
                req.getSampleBarcode(), req.getSampleId(), req.getSampleType(), req.getPlanId(),
                req.getTestItemIds(),
                req.getPatientName(), req.getOperatorId(), req.getOperatorName()));
    }

    /** 按标本ID查询已生成的培养皿 */
    @GetMapping("/by-sample/{sampleId}")
    public R<List<SamplePlate>> bySample(@PathVariable Long sampleId) {
        return R.ok(samplePlateService.listBySampleId(sampleId));
    }

    /** 按标本条码查询已生成的培养皿 */
    @GetMapping("/by-barcode/{barcode}")
    public R<List<SamplePlate>> byBarcode(@PathVariable String barcode) {
        return R.ok(samplePlateService.listBySampleBarcode(barcode));
    }

    /**
     * 取消单条培养基条码生成（作废）。
     * 仅允许取消未核收的；已核收需先撤销微生物报告。
     */
    @DeleteMapping("/{id:\\d+}")
    public R<Void> cancelPlate(@PathVariable Long id) {
        samplePlateService.cancelPlate(id);
        return R.ok();
    }

    /**
     * 批量取消某标本全部培养基条码生成（作废）。
     * 参数：sampleId 或 sampleBarcode 二选一
     */
    @DeleteMapping("/cancel-by-sample")
    public R<Void> cancelAllBySample(@RequestParam(required = false) Long sampleId,
                                     @RequestParam(required = false) String sampleBarcode) {
        samplePlateService.cancelAllBySample(sampleId, sampleBarcode);
        return R.ok();
    }
}