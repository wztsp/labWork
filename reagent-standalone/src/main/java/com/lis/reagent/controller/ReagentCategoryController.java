package com.lis.reagent.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.lis.common.core.result.PageResult;
import com.lis.common.core.result.R;
import com.lis.reagent.dto.CategoryCreateDTO;
import com.lis.reagent.entity.ReagentCategory;
import com.lis.reagent.service.ReagentCategoryService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 试剂分类字典 Controller（R-06）。
 *
 * <p>分类树 + 危险品标志（{@code hazardCategory}：0 普通 / 1 危险品类）。
 * 危险品列 {@code lis_reagent_category.hazard_category} 已在 T-01 建表时落地，<b>无需改 DDL</b>。</p>
 *
 * @author LIS Team
 */
@RestController
@RequestMapping("/api/reagent/category")
@RequiredArgsConstructor
public class ReagentCategoryController {

    private final ReagentCategoryService categoryService;

    /** 分类树（含 children 子树） */
    @GetMapping("/tree")
    public R<List<ReagentCategory>> tree() {
        return R.ok(categoryService.tree());
    }

    /** 分页查询 */
    @GetMapping("/page")
    public R<PageResult<ReagentCategory>> page(@RequestParam(defaultValue = "1") long current,
                                               @RequestParam(defaultValue = "10") long size,
                                               @RequestParam(required = false) String keyword,
                                               @RequestParam(required = false) Integer status,
                                               @RequestParam(required = false) Integer hazardCategory) {
        Page<ReagentCategory> page = categoryService.queryPage(current, size, keyword, status, hazardCategory);
        return R.ok(new PageResult<>(page.getRecords(), page.getTotal(), page.getCurrent(), page.getSize()));
    }

    /** 详情 */
    @GetMapping("/{id}")
    public R<ReagentCategory> getById(@PathVariable Long id) {
        return R.ok(categoryService.getById(id));
    }

    /** 新增分类 */
    @PostMapping
    public R<ReagentCategory> create(@RequestBody CategoryCreateDTO dto) {
        return R.ok(categoryService.create(dto));
    }

    /** 更新分类 */
    @PutMapping
    public R<ReagentCategory> update(@RequestBody CategoryCreateDTO dto) {
        return R.ok(categoryService.update(dto));
    }

    /** 删除分类（有子节点 / 被试剂引用时拒绝） */
    @DeleteMapping("/{id}")
    public R<Void> delete(@PathVariable Long id) {
        categoryService.delete(id);
        return R.ok();
    }
}
