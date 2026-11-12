package com.lis.base.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.lis.base.entity.TestItem;
import com.lis.base.service.TestItemService;
import com.lis.common.core.result.PageResult;
import com.lis.common.core.result.R;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 检验项目 Controller
 */
@RestController
@RequestMapping("/api/base/items")
@RequiredArgsConstructor
public class TestItemController {

    private final TestItemService testItemService;

    @GetMapping
    public R<PageResult<TestItem>> page(@RequestParam(defaultValue = "1") long current,
                                        @RequestParam(defaultValue = "10") long size,
                                        @RequestParam(required = false) String keyword,
                                        @RequestParam(required = false) String category,
                                        @RequestParam(required = false) Integer isPanel,
                                        @RequestParam(required = false) Integer status,
                                        @RequestParam(required = false) String specimenType) {
        Page<TestItem> page = testItemService.queryPage(current, size, keyword, category, isPanel, status, specimenType);
        return R.ok(new PageResult<>(page.getRecords(), page.getTotal(), page.getCurrent(), page.getSize()));
    }

    @GetMapping("/{id}")
    public R<TestItem> getById(@PathVariable Long id) {
        return R.ok(testItemService.getById(id));
    }

    @GetMapping("/code/{code}")
    public R<TestItem> getByCode(@PathVariable String code) {
        return R.ok(testItemService.getByCode(code));
    }

    @PostMapping
    public R<TestItem> create(@RequestBody TestItem testItem) {
        return R.ok(testItemService.create(testItem));
    }

    @PutMapping
    public R<TestItem> update(@RequestBody TestItem testItem) {
        return R.ok(testItemService.update(testItem));
    }

    @DeleteMapping("/{id}")
    public R<Void> delete(@PathVariable Long id) {
        testItemService.delete(id);
        return R.ok();
    }

    /**
     * 查询检验项目下的子项目
     */
    @GetMapping("/{id}/panel-items")
    public R<List<TestItem>> listPanelItems(@PathVariable Long id) {
        return R.ok(testItemService.listPanelItems(id));
    }

    /**
     * 管理检验项目(设置子项目)
     */
    @PostMapping("/{id}/panel")
    public R<Void> managePanel(@PathVariable Long id, @RequestBody List<Long> childIds) {
        testItemService.managePanel(id, childIds);
        return R.ok();
    }

    /**
     * 加载项目字典缓存
     */
    @GetMapping("/dict")
    public R<List<TestItem>> loadDict() {
        return R.ok(testItemService.loadDictFromCache());
    }
}
