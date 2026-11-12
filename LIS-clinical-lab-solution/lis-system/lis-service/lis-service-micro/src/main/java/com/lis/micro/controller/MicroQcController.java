package com.lis.micro.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.lis.common.core.result.PageResult;
import com.lis.common.core.result.R;
import com.lis.micro.entity.MicroQcRecord;
import com.lis.micro.service.MicroQcService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * 微生物室内质控 Controller
 */
@RestController
@RequestMapping("/api/micro/qc")
@RequiredArgsConstructor
public class MicroQcController {

    private final MicroQcService microQcService;

    @GetMapping
    public R<PageResult<MicroQcRecord>> page(@RequestParam(defaultValue = "1") long current,
                                             @RequestParam(defaultValue = "20") long size,
                                             @RequestParam(required = false) Integer itemType,
                                             @RequestParam(required = false) String keyword,
                                             @RequestParam(required = false) String result) {
        Page<MicroQcRecord> page = microQcService.queryPage(current, size, itemType, keyword, result);
        return R.ok(new PageResult<>(page.getRecords(), page.getTotal(), page.getCurrent(), page.getSize()));
    }

    @GetMapping("/stats")
    public R<Map<String, Object>> stats(@RequestParam(required = false) Integer itemType) {
        return R.ok(microQcService.stats(itemType));
    }

    @PostMapping
    public R<MicroQcRecord> create(@RequestBody MicroQcRecord record) {
        return R.ok(microQcService.saveRecord(record));
    }

    @PutMapping
    public R<MicroQcRecord> update(@RequestBody MicroQcRecord record) {
        return R.ok(microQcService.saveRecord(record));
    }

    @DeleteMapping("/{id}")
    public R<Void> delete(@PathVariable Long id) {
        microQcService.removeById(id);
        return R.ok();
    }
}