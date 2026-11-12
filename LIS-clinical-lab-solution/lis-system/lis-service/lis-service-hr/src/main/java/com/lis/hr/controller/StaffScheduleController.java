package com.lis.hr.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.lis.common.core.result.PageResult;
import com.lis.common.core.result.R;
import com.lis.hr.entity.StaffSchedule;
import com.lis.hr.service.StaffScheduleService;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

/**
 * 排班 Controller
 */
@RestController
@RequestMapping("/api/hr/schedules")
@RequiredArgsConstructor
public class StaffScheduleController {

    /** 批量指定班次请求体 */
    @Data
    public static class BatchAssignDTO {
        private List<Long> staffIds;
        private List<LocalDate> dates;
        private String shiftType;
        private String dutyType;
    }

    private final StaffScheduleService scheduleService;

    /**
     * 分页查询全部排班 (前端管理页面用)
     */
    @GetMapping
    public R<PageResult<StaffSchedule>> page(@RequestParam(defaultValue = "1") long current,
                                              @RequestParam(defaultValue = "10") long size,
                                              @RequestParam(required = false) String staffName,
                                              @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate scheduleDate,
                                              @RequestParam(required = false) String shiftType) {
        Page<StaffSchedule> page = new Page<>(current, size);
        scheduleService.page(page, staffName, scheduleDate, shiftType);
        return R.ok(new PageResult<>(page.getRecords(), page.getTotal(), page.getCurrent(), page.getSize()));
    }

    @GetMapping("/date/{date}")
    public R<List<StaffSchedule>> listByDate(@PathVariable @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return R.ok(scheduleService.listByDate(date));
    }

    @GetMapping("/staff/{staffId}")
    public R<List<StaffSchedule>> listByStaff(@PathVariable Long staffId) {
        return R.ok(scheduleService.listByStaffId(staffId));
    }

    @GetMapping("/range")
    public R<List<StaffSchedule>> listByRange(@RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate start,
                                              @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate end) {
        return R.ok(scheduleService.listByRange(start, end));
    }

    @PostMapping("/batch")
    public R<Integer> assignBatch(@RequestBody BatchAssignDTO dto) {
        int count = scheduleService.assignBatch(dto.getStaffIds(), dto.getDates(), dto.getShiftType(), dto.getDutyType());
        return R.ok(count);
    }

    @PostMapping
    public R<StaffSchedule> create(@RequestBody StaffSchedule schedule) {
        return R.ok(scheduleService.create(schedule));
    }

    @PutMapping
    public R<StaffSchedule> update(@RequestBody StaffSchedule schedule) {
        return R.ok(scheduleService.update(schedule));
    }

    @DeleteMapping("/{id}")
    public R<Void> delete(@PathVariable Long id) {
        scheduleService.delete(id);
        return R.ok();
    }
}
