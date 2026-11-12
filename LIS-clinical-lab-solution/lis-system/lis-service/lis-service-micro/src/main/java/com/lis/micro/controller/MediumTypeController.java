package com.lis.micro.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.lis.common.core.result.PageResult;
import com.lis.common.core.result.R;
import com.lis.micro.entity.MediumType;
import com.lis.micro.service.MediumTypeService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 培养基类型字典 Controller
 */
@RestController
@RequestMapping("/api/micro/medium-type")
@RequiredArgsConstructor
public class MediumTypeController {

    private final MediumTypeService mediumTypeService;

    @GetMapping
    public R<PageResult<MediumType>> page(@RequestParam(defaultValue = "1") long current,
                                          @RequestParam(defaultValue = "20") long size,
                                          @RequestParam(required = false) String keyword) {
        Page<MediumType> page = mediumTypeService.queryPage(current, size, keyword);
        return R.ok(new PageResult<>(page.getRecords(), page.getTotal(), page.getCurrent(), page.getSize()));
    }

    @GetMapping("/all")
    public R<List<MediumType>> listAll() {
        return R.ok(mediumTypeService.listEnabled());
    }

    @GetMapping("/{id}")
    public R<MediumType> getById(@PathVariable Long id) {
        return R.ok(mediumTypeService.getById(id));
    }

    @PostMapping
    public R<MediumType> create(@RequestBody MediumType mediumType) {
        if (mediumType.getStatus() == null) {
            mediumType.setStatus(1);
        }
        mediumTypeService.save(mediumType);
        return R.ok(mediumType);
    }

    @PutMapping
    public R<MediumType> update(@RequestBody MediumType mediumType) {
        mediumTypeService.updateById(mediumType);
        return R.ok(mediumType);
    }

    @DeleteMapping("/{id}")
    public R<Void> delete(@PathVariable Long id) {
        mediumTypeService.removeById(id);
        return R.ok();
    }
}