package com.lis.base.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.lis.base.entity.InstrumentRepair;
import com.lis.base.service.InstrumentRepairService;
import com.lis.common.core.result.PageResult;
import com.lis.common.core.result.R;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

/**
 * 仪器维修记录 Controller
 */
@RestController
@RequestMapping("/api/instrument/repair")
@RequiredArgsConstructor
public class InstrumentRepairController {

    private final InstrumentRepairService instrumentRepairService;

    /**
     * 分页查询维修记录
     */
    @GetMapping
    public R<PageResult<InstrumentRepair>> page(@RequestParam(defaultValue = "1") long current,
                                                @RequestParam(defaultValue = "10") long size,
                                                @RequestParam(required = false) Long instrumentId) {
        Page<InstrumentRepair> page = instrumentRepairService.queryPage(current, size, instrumentId);
        return R.ok(new PageResult<>(page.getRecords(), page.getTotal(), page.getCurrent(), page.getSize()));
    }

    /**
     * 新增维修记录
     */
    @PostMapping
    public R<InstrumentRepair> create(@RequestBody InstrumentRepair repair) {
        return R.ok(instrumentRepairService.create(repair));
    }

    /**
     * 修改维修记录
     */
    @PutMapping
    public R<InstrumentRepair> update(@RequestBody InstrumentRepair repair) {
        return R.ok(instrumentRepairService.update(repair));
    }

    /**
     * 删除维修记录
     */
    @DeleteMapping("/{id}")
    public R<Void> delete(@PathVariable Long id) {
        instrumentRepairService.delete(id);
        return R.ok();
    }
}
