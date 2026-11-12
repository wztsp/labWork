package com.lis.hr.controller;

import com.lis.common.core.result.R;
import com.lis.hr.entity.TitleDict;
import com.lis.hr.service.TitleDictService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 职称字典 Controller
 */
@RestController
@RequestMapping("/api/hr/title-dict")
@RequiredArgsConstructor
public class TitleDictController {

    private final TitleDictService titleDictService;

    @GetMapping
    public R<List<TitleDict>> list() {
        return R.ok(titleDictService.listAll());
    }

    @PostMapping
    public R<TitleDict> create(@RequestBody TitleDict titleDict) {
        return R.ok(titleDictService.create(titleDict));
    }

    @PutMapping
    public R<TitleDict> update(@RequestBody TitleDict titleDict) {
        return R.ok(titleDictService.update(titleDict));
    }

    @DeleteMapping("/{id}")
    public R<Void> delete(@PathVariable Long id) {
        titleDictService.delete(id);
        return R.ok();
    }
}
