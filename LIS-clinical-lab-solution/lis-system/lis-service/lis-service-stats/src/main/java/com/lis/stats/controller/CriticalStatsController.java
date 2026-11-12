package com.lis.stats.controller;

import com.lis.common.core.result.R;
import com.lis.stats.service.CriticalStatsService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 危急值统计 Controller
 */
@RestController
@RequestMapping("/api/stats/critical")
@RequiredArgsConstructor
public class CriticalStatsController {

    private final CriticalStatsService criticalStatsService;

    /** 危急值汇总: 总数、已确认数、确认率、平均确认时长(秒) */
    @GetMapping
    public R<Map<String, Object>> summary(
            @RequestParam(required = false) String startDate,
            @RequestParam(required = false) String endDate) {
        return R.ok(criticalStatsService.getCriticalSummary(startDate, endDate));
    }

    /** 按日统计危急值 */
    @GetMapping("/by-day")
    public R<List<Map<String, Object>>> byDay(
            @RequestParam(required = false) String startDate,
            @RequestParam(required = false) String endDate) {
        return R.ok(criticalStatsService.getCriticalByDay(startDate, endDate));
    }

    /** 按项目统计危急值 */
    @GetMapping("/by-item")
    public R<List<Map<String, Object>>> byItem(
            @RequestParam(required = false) String startDate,
            @RequestParam(required = false) String endDate) {
        return R.ok(criticalStatsService.getCriticalByItem(startDate, endDate));
    }

    /** 按科室统计危急值 */
    @GetMapping("/by-dept")
    public R<List<Map<String, Object>>> byDept(
            @RequestParam(required = false) String startDate,
            @RequestParam(required = false) String endDate) {
        return R.ok(criticalStatsService.getCriticalByDept(startDate, endDate));
    }
}
