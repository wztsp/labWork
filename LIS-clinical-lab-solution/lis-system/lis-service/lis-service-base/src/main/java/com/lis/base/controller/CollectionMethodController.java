package com.lis.base.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.lis.base.entity.CollectionMethod;
import com.lis.base.service.CollectionMethodService;
import com.lis.common.core.result.PageResult;
import com.lis.common.core.result.R;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 采集方式 Controller
 */
@RestController
@RequestMapping("/api/base/collection-methods")
@RequiredArgsConstructor
public class CollectionMethodController {

    private final CollectionMethodService collectionMethodService;

    /**
     * 分页查询
     */
    @GetMapping
    public R<PageResult<CollectionMethod>> page(@RequestParam(defaultValue = "1") long current,
                                                 @RequestParam(defaultValue = "10") long size,
                                                 @RequestParam(required = false) String keyword,
                                                 @RequestParam(required = false) Integer status) {
        Page<CollectionMethod> page = collectionMethodService.queryPage(current, size, keyword, status);
        return R.ok(new PageResult<>(page.getRecords(), page.getTotal(), page.getCurrent(), page.getSize()));
    }

    /**
     * 查询全部启用的采集方式 (供下拉选择)
     */
    @GetMapping("/all")
    public R<List<CollectionMethod>> listAll() {
        return R.ok(collectionMethodService.listAllEnabled());
    }

    @GetMapping("/{id}")
    public R<CollectionMethod> getById(@PathVariable Long id) {
        return R.ok(collectionMethodService.getById(id));
    }

    @PostMapping
    public R<CollectionMethod> create(@RequestBody CollectionMethod collectionMethod) {
        return R.ok(collectionMethodService.create(collectionMethod));
    }

    @PutMapping
    public R<CollectionMethod> update(@RequestBody CollectionMethod collectionMethod) {
        return R.ok(collectionMethodService.update(collectionMethod));
    }

    @DeleteMapping("/{id}")
    public R<Void> delete(@PathVariable Long id) {
        collectionMethodService.delete(id);
        return R.ok();
    }
}
