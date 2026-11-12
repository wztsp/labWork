package com.lis.monolith.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.lis.common.core.result.R;
import com.lis.monolith.entity.TatMonitor;
import com.lis.monolith.service.TatMonitorService;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.Map;

/**
 * TAT 业务数据监控
 */
@RestController
@RequestMapping("/api/tat/monitor")
@RequiredArgsConstructor
public class TatMonitorController {

    private final TatMonitorService tatMonitorService;

    @GetMapping("/page")
    public R<IPage<TatMonitor>> page(@RequestParam(defaultValue = "1") int page,
                                     @RequestParam(defaultValue = "10") int size,
                                     @RequestParam(required = false) Integer isOverdue,
                                     @RequestParam(required = false) String keyword,
                                     @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd") LocalDate startDate,
                                     @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd") LocalDate endDate) {
        LocalDateTime startTime = startDate != null ? startDate.atStartOfDay() : null;
        LocalDateTime endTime = endDate != null ? endDate.atTime(LocalTime.MAX) : null;
        return R.ok(tatMonitorService.page(page, size, isOverdue, keyword, startTime, endTime));
    }

    @GetMapping("/{id}")
    public R<TatMonitor> detail(@PathVariable Long id) {
        return R.ok(tatMonitorService.getById(id));
    }

    @GetMapping("/stats")
    public R<Map<String, Object>> stats(@RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd") LocalDate startDate,
                                        @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd") LocalDate endDate) {
        LocalDateTime startTime = startDate != null ? startDate.atStartOfDay() : null;
        LocalDateTime endTime = endDate != null ? endDate.atTime(LocalTime.MAX) : null;
        return R.ok(tatMonitorService.stats(startTime, endTime));
    }

    /** 手动触发一次扫描 */
    @PostMapping("/refresh")
    public R<Integer> refresh() {
        return R.ok(tatMonitorService.runScan());
    }
}