package com.lis.stats.controller;

import com.lis.common.core.result.R;
import com.lis.stats.service.WorkloadStatsService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 工作量统计 Controller
 */
@RestController
@RequestMapping("/api/stats/workload")
@RequiredArgsConstructor
public class WorkloadStatsController {

    private final WorkloadStatsService workloadStatsService;

    /** 工作量汇总: 总标本量、今日标本量、急诊标本量、已报告数 */
    @GetMapping
    public R<Map<String, Object>> summary() {
        return R.ok(workloadStatsService.getWorkloadSummary());
    }

    /** 按日统计标本量 (柱状图数据) */
    @GetMapping("/by-day")
    public R<List<Map<String, Object>>> byDay(
            @RequestParam(required = false) String startDate,
            @RequestParam(required = false) String endDate) {
        return R.ok(workloadStatsService.getWorkloadByDay(startDate, endDate));
    }

    /** 按科室统计标本量 (饼图数据) */
    @GetMapping("/by-dept")
    public R<List<Map<String, Object>>> byDept(
            @RequestParam(required = false) String startDate,
            @RequestParam(required = false) String endDate) {
        return R.ok(workloadStatsService.getWorkloadByDept(startDate, endDate));
    }

    /** 按仪器统计标本量 */
    @GetMapping("/by-instrument")
    public R<List<Map<String, Object>>> byInstrument(
            @RequestParam(required = false) String startDate,
            @RequestParam(required = false) String endDate) {
        return R.ok(workloadStatsService.getWorkloadByInstrument(startDate, endDate));
    }
}
