package com.lis.hr.controller;

import com.lis.common.core.result.R;
import com.lis.hr.entity.LabGroup;
import com.lis.hr.service.LabGroupService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 小组/科室 Controller
 */
@RestController
@RequestMapping("/api/hr/groups")
@RequiredArgsConstructor
public class LabGroupController {

    private final LabGroupService labGroupService;

    @GetMapping
    public R<List<LabGroup>> list() {
        return R.ok(labGroupService.listAll());
    }

    @GetMapping("/tree")
    public R<List<LabGroup>> tree() {
        return R.ok(labGroupService.listTree());
    }

    @GetMapping("/{id}")
    public R<LabGroup> getById(@PathVariable Long id) {
        return R.ok(labGroupService.getById(id));
    }

    @PostMapping
    public R<LabGroup> create(@RequestBody LabGroup group) {
        return R.ok(labGroupService.create(group));
    }

    @PutMapping
    public R<LabGroup> update(@RequestBody LabGroup group) {
        return R.ok(labGroupService.update(group));
    }

    @DeleteMapping("/{id}")
    public R<Void> delete(@PathVariable Long id) {
        labGroupService.delete(id);
        return R.ok();
    }
}
