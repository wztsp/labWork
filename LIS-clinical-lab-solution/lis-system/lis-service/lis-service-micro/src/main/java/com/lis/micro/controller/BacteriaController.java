package com.lis.micro.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.lis.common.core.result.PageResult;
import com.lis.common.core.result.R;
import com.lis.micro.entity.Bacteria;
import com.lis.micro.service.BacteriaService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 细菌字典 Controller
 */
@RestController
@RequestMapping("/api/micro/bacteria")
@RequiredArgsConstructor
public class BacteriaController {

    private final BacteriaService bacteriaService;

    @GetMapping
    public R<PageResult<Bacteria>> page(@RequestParam(defaultValue = "1") long current,
                                         @RequestParam(defaultValue = "20") long size,
                                         @RequestParam(required = false) String keyword,
                                         @RequestParam(required = false) String gramType,
                                         @RequestParam(required = false) String category) {
        Page<Bacteria> page = bacteriaService.queryPage(current, size, keyword, gramType, category);
        return R.ok(new PageResult<>(page.getRecords(), page.getTotal(), page.getCurrent(), page.getSize()));
    }

    @GetMapping("/all")
    public R<List<Bacteria>> listAll() {
        return R.ok(bacteriaService.listAllEnabled());
    }

    @GetMapping("/common")
    public R<List<Bacteria>> listCommon() {
        return R.ok(bacteriaService.listCommon());
    }

    @GetMapping("/{id}")
    public R<Bacteria> getById(@PathVariable Long id) {
        return R.ok(bacteriaService.getById(id));
    }

    @GetMapping("/code/{code}")
    public R<Bacteria> getByCode(@PathVariable String code) {
        return R.ok(bacteriaService.getByCode(code));
    }

    @PostMapping
    public R<Bacteria> create(@RequestBody Bacteria bacteria) {
        bacteria.setStatus(1);
        bacteriaService.save(bacteria);
        return R.ok(bacteria);
    }

    @PutMapping
    public R<Bacteria> update(@RequestBody Bacteria bacteria) {
        bacteriaService.updateById(bacteria);
        return R.ok(bacteria);
    }

    @DeleteMapping("/{id}")
    public R<Void> delete(@PathVariable Long id) {
        bacteriaService.removeById(id);
        return R.ok();
    }
}
