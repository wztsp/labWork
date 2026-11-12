package com.lis.qc.controller;

import com.lis.common.core.result.R;
import com.lis.qc.entity.QCLot;
import com.lis.qc.service.QCLotService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 质控品批号 CRUD
 */
@RestController
@RequestMapping("/api/qc/lots")
@RequiredArgsConstructor
public class QCLotController {

    private final QCLotService qcLotService;

    @PostMapping
    public R<Long> create(@RequestBody QCLot lot) {
        return R.ok(qcLotService.create(lot));
    }

    @PutMapping
    public R<Boolean> update(@RequestBody QCLot lot) {
        return R.ok(qcLotService.update(lot));
    }

    @DeleteMapping("/{id}")
    public R<Boolean> delete(@PathVariable Long id) {
        return R.ok(qcLotService.delete(id));
    }

    @GetMapping("/{id}")
    public R<QCLot> getById(@PathVariable Long id) {
        return R.ok(qcLotService.getById(id));
    }

    @GetMapping("/list")
    public R<List<QCLot>> listAll() {
        return R.ok(qcLotService.listAll());
    }

    @GetMapping("/enabled")
    public R<List<QCLot>> listEnabled() {
        return R.ok(qcLotService.listEnabled());
    }

    /**
     * 重估靶值与标准差: 基于该批号历史在控质控记录, 使用样本均值和Bessel修正的样本标准差,
     * 写回 lis_qc_lot.target_value / sd / cv. 最小样本数默认 20.
     * ?minSamples=10 可覆盖.
     */
    @PostMapping("/{id}/reestimate")
    public R<Map<String, Object>> reestimate(@PathVariable Long id,
                                             @RequestParam(required = false) Integer minSamples,
                                             @RequestParam(defaultValue = "admin") String operator) {
        return R.ok(qcLotService.reestimate(id, minSamples, operator));
    }
}
