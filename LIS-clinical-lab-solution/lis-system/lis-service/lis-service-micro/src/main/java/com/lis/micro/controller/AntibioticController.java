package com.lis.micro.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.lis.common.core.result.PageResult;
import com.lis.common.core.result.R;
import com.lis.micro.entity.Antibiotic;
import com.lis.micro.service.AntibioticService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 抗生素字典 Controller
 */
@RestController
@RequestMapping("/api/micro/antibiotics")
@RequiredArgsConstructor
public class AntibioticController {

    private final AntibioticService antibioticService;

    @GetMapping
    public R<PageResult<Antibiotic>> page(@RequestParam(defaultValue = "1") long current,
                                           @RequestParam(defaultValue = "20") long size,
                                           @RequestParam(required = false) String keyword,
                                           @RequestParam(required = false) String category) {
        Page<Antibiotic> page = antibioticService.queryPage(current, size, keyword, category);
        return R.ok(new PageResult<>(page.getRecords(), page.getTotal(), page.getCurrent(), page.getSize()));
    }

    @GetMapping("/all")
    public R<List<Antibiotic>> listAll() {
        return R.ok(antibioticService.listAllEnabled());
    }

    @GetMapping("/{id}")
    public R<Antibiotic> getById(@PathVariable Long id) {
        return R.ok(antibioticService.getById(id));
    }

    @GetMapping("/code/{code}")
    public R<Antibiotic> getByCode(@PathVariable String code) {
        return R.ok(antibioticService.getByCode(code));
    }

    @PostMapping
    public R<Antibiotic> create(@RequestBody Antibiotic antibiotic) {
        antibiotic.setStatus(1);
        antibioticService.save(antibiotic);
        return R.ok(antibiotic);
    }

    @PutMapping
    public R<Antibiotic> update(@RequestBody Antibiotic antibiotic) {
        antibioticService.updateById(antibiotic);
        return R.ok(antibiotic);
    }

    @DeleteMapping("/{id}")
    public R<Void> delete(@PathVariable Long id) {
        antibioticService.removeById(id);
        return R.ok();
    }
}
