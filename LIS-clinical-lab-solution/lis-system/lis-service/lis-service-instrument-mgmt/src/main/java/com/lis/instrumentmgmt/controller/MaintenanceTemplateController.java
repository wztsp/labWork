package com.lis.instrumentmgmt.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.lis.common.core.result.PageResult;
import com.lis.common.core.result.R;
import com.lis.instrumentmgmt.entity.MaintenanceTemplate;
import com.lis.instrumentmgmt.entity.MaintenanceTemplateItem;
import com.lis.instrumentmgmt.service.MaintenanceTemplateService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 维护模板 Controller
 */
@RestController
@RequestMapping("/api/instrument-mgmt/templates")
@RequiredArgsConstructor
public class MaintenanceTemplateController {

    private final MaintenanceTemplateService templateService;

    @GetMapping
    public R<PageResult<MaintenanceTemplate>> page(@RequestParam(defaultValue = "1") long current,
                                                   @RequestParam(defaultValue = "10") long size,
                                                   @RequestParam(required = false) String keyword,
                                                   @RequestParam(required = false) String cycleType) {
        Page<MaintenanceTemplate> page = templateService.queryPage(current, size, keyword, cycleType);
        return R.ok(new PageResult<>(page.getRecords(), page.getTotal(), page.getCurrent(), page.getSize()));
    }

    @GetMapping("/{id}")
    public R<MaintenanceTemplate> getById(@PathVariable Long id) {
        return R.ok(templateService.getById(id));
    }

    @GetMapping("/code/{code}")
    public R<MaintenanceTemplate> getByCode(@PathVariable String code) {
        return R.ok(templateService.getByCode(code));
    }

    @PostMapping
    public R<MaintenanceTemplate> create(@RequestBody MaintenanceTemplate template) {
        templateService.save(template);
        return R.ok(template);
    }

    @PutMapping
    public R<MaintenanceTemplate> update(@RequestBody MaintenanceTemplate template) {
        templateService.updateById(template);
        return R.ok(template);
    }

    @DeleteMapping("/{id}")
    public R<Void> delete(@PathVariable Long id) {
        templateService.removeById(id);
        return R.ok();
    }

    /** 模板明细 */
    @GetMapping("/{id}/items")
    public R<List<MaintenanceTemplateItem>> listItems(@PathVariable Long id) {
        return R.ok(templateService.listItems(id));
    }

    /** 保存模板明细 (覆盖) */
    @PostMapping("/{id}/items")
    public R<Void> saveItems(@PathVariable Long id, @RequestBody List<MaintenanceTemplateItem> items) {
        templateService.saveItems(id, items);
        return R.ok();
    }
}
