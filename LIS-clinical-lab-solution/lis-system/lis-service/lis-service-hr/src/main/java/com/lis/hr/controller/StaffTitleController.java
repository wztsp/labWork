package com.lis.hr.controller;

import com.lis.common.core.result.R;
import com.lis.hr.entity.StaffTitle;
import com.lis.hr.service.StaffTitleService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 人员职称 Controller
 */
@RestController
@RequestMapping("/api/hr/titles")
@RequiredArgsConstructor
public class StaffTitleController {

    private final StaffTitleService staffTitleService;

    /**
     * 全部人员职称记录 (管理页面用)
     */
    @GetMapping
    public R<List<StaffTitle>> listAll() {
        return R.ok(staffTitleService.list());
    }

    @GetMapping("/staff/{staffId}")
    public R<List<StaffTitle>> listByStaff(@PathVariable Long staffId) {
        return R.ok(staffTitleService.listByStaffId(staffId));
    }

    @GetMapping("/staff/{staffId}/current")
    public R<StaffTitle> getCurrent(@PathVariable Long staffId) {
        return R.ok(staffTitleService.getCurrentTitle(staffId));
    }

    @PostMapping
    public R<StaffTitle> create(@RequestBody StaffTitle staffTitle) {
        return R.ok(staffTitleService.create(staffTitle));
    }

    @PutMapping
    public R<StaffTitle> update(@RequestBody StaffTitle staffTitle) {
        return R.ok(staffTitleService.update(staffTitle));
    }

    @DeleteMapping("/{id}")
    public R<Void> delete(@PathVariable Long id) {
        staffTitleService.delete(id);
        return R.ok();
    }
}
