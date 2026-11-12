package com.lis.base.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.lis.base.entity.InstrumentCalibration;
import com.lis.base.service.InstrumentCalibrationService;
import com.lis.common.core.result.PageResult;
import com.lis.common.core.result.R;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

/**
 * 仪器校准记录 Controller
 */
@RestController
@RequestMapping("/api/instrument/calibration")
@RequiredArgsConstructor
public class InstrumentCalibrationController {

    private final InstrumentCalibrationService instrumentCalibrationService;

    /**
     * 分页查询校准记录
     */
    @GetMapping
    public R<PageResult<InstrumentCalibration>> page(@RequestParam(defaultValue = "1") long current,
                                                     @RequestParam(defaultValue = "10") long size,
                                                     @RequestParam(required = false) Long instrumentId) {
        Page<InstrumentCalibration> page = instrumentCalibrationService.queryPage(current, size, instrumentId);
        return R.ok(new PageResult<>(page.getRecords(), page.getTotal(), page.getCurrent(), page.getSize()));
    }

    /**
     * 新增校准记录
     */
    @PostMapping
    public R<InstrumentCalibration> create(@RequestBody InstrumentCalibration calibration) {
        return R.ok(instrumentCalibrationService.create(calibration));
    }

    /**
     * 修改校准记录
     */
    @PutMapping
    public R<InstrumentCalibration> update(@RequestBody InstrumentCalibration calibration) {
        return R.ok(instrumentCalibrationService.update(calibration));
    }

    /**
     * 删除校准记录
     */
    @DeleteMapping("/{id}")
    public R<Void> delete(@PathVariable Long id) {
        instrumentCalibrationService.delete(id);
        return R.ok();
    }
}
