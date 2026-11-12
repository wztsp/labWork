package com.lis.base.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.lis.base.entity.SpecimenType;
import com.lis.base.service.SpecimenTypeService;
import com.lis.common.core.result.PageResult;
import com.lis.common.core.result.R;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 标本类型 Controller
 */
@RestController
@RequestMapping("/api/base/specimen-types")
@RequiredArgsConstructor
public class SpecimenTypeController {

    private final SpecimenTypeService specimenTypeService;

    /**
     * 分页查询
     */
    @GetMapping
    public R<PageResult<SpecimenType>> page(@RequestParam(defaultValue = "1") long current,
                                             @RequestParam(defaultValue = "10") long size,
                                             @RequestParam(required = false) String keyword,
                                             @RequestParam(required = false) Integer status) {
        Page<SpecimenType> page = specimenTypeService.queryPage(current, size, keyword, status);
        return R.ok(new PageResult<>(page.getRecords(), page.getTotal(), page.getCurrent(), page.getSize()));
    }

    /**
     * 查询全部启用的标本类型 (供下拉选择)
     */
    @GetMapping("/all")
    public R<List<SpecimenType>> listAll() {
        return R.ok(specimenTypeService.listAllEnabled());
    }

    @GetMapping("/{id}")
    public R<SpecimenType> getById(@PathVariable Long id) {
        return R.ok(specimenTypeService.getById(id));
    }

    @PostMapping
    public R<SpecimenType> create(@RequestBody SpecimenType specimenType) {
        return R.ok(specimenTypeService.create(specimenType));
    }

    @PutMapping
    public R<SpecimenType> update(@RequestBody SpecimenType specimenType) {
        return R.ok(specimenTypeService.update(specimenType));
    }

    @DeleteMapping("/{id}")
    public R<Void> delete(@PathVariable Long id) {
        specimenTypeService.delete(id);
        return R.ok();
    }
}
