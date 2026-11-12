package com.lis.hr.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.lis.common.core.result.PageResult;
import com.lis.common.core.result.R;
import com.lis.hr.entity.Staff;
import com.lis.hr.service.StaffService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 人员档案 Controller
 */
@RestController
@RequestMapping("/api/hr/staff")
@RequiredArgsConstructor
public class StaffController {

    private final StaffService staffService;

    @GetMapping
    public R<PageResult<Staff>> page(@RequestParam(defaultValue = "1") long current,
                                      @RequestParam(defaultValue = "20") long size,
                                      @RequestParam(required = false) String keyword,
                                      @RequestParam(required = false) Long groupId,
                                      @RequestParam(required = false) Integer status) {
        Page<Staff> page = staffService.queryPage(current, size, keyword, groupId, status);
        return R.ok(new PageResult<>(page.getRecords(), page.getTotal(), page.getCurrent(), page.getSize()));
    }

    @GetMapping("/{id}")
    public R<Staff> getById(@PathVariable Long id) {
        return R.ok(staffService.getById(id));
    }

    @GetMapping("/staff-no/{staffNo}")
    public R<Staff> getByStaffNo(@PathVariable String staffNo) {
        return R.ok(staffService.getByStaffNo(staffNo));
    }

    @GetMapping("/group/{groupId}")
    public R<List<Staff>> listByGroup(@PathVariable Long groupId) {
        return R.ok(staffService.listByGroupId(groupId));
    }

    @PostMapping
    public R<Staff> create(@RequestBody Staff staff) {
        return R.ok(staffService.create(staff));
    }

    @PutMapping
    public R<Staff> update(@RequestBody Staff staff) {
        return R.ok(staffService.update(staff));
    }

    @PostMapping("/{id}/resign")
    public R<Void> resign(@PathVariable Long id) {
        staffService.resign(id);
        return R.ok();
    }

    @DeleteMapping("/{id}")
    public R<Void> delete(@PathVariable Long id) {
        staffService.delete(id);
        return R.ok();
    }

    /**
     * 即将到期证书预警
     */
    @GetMapping("/certificates/expiring")
    public R<List<Staff>> listExpiringCerts(@RequestParam(defaultValue = "30") int withinDays) {
        return R.ok(staffService.listStaffWithExpiringCertificates(withinDays));
    }
}
