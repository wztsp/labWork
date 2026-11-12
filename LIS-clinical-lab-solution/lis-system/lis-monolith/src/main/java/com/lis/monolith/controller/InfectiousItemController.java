package com.lis.monolith.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.lis.common.core.result.R;
import com.lis.monolith.entity.InfectiousItem;
import com.lis.monolith.service.InfectiousItemService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

/**
 * 传染病项目配置
 */
@RestController
@RequestMapping("/api/infectious")
@RequiredArgsConstructor
public class InfectiousItemController {

    private final InfectiousItemService infectiousItemService;

    @GetMapping("/page")
    public R<IPage<InfectiousItem>> page(@RequestParam(defaultValue = "1") int page,
                                         @RequestParam(defaultValue = "10") int size,
                                         @RequestParam(required = false) String keyword) {
        return R.ok(infectiousItemService.page(page, size, keyword));
    }

    @PostMapping
    public R<InfectiousItem> create(@RequestBody InfectiousItem item) {
        return R.ok(infectiousItemService.create(item));
    }

    @PutMapping
    public R<InfectiousItem> update(@RequestBody InfectiousItem item) {
        return R.ok(infectiousItemService.update(item));
    }

    @DeleteMapping("/{id}")
    public R<Void> delete(@PathVariable Long id) {
        infectiousItemService.delete(id);
        return R.ok();
    }
}