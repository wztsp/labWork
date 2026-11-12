package com.lis.stats.controller;

import com.lis.common.core.result.R;
import com.lis.stats.service.ChargeStatsService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 收费统计 Controller
 */
@RestController
@RequestMapping("/api/stats/charge")
@RequiredArgsConstructor
public class ChargeStatsController {

    private final ChargeStatsService chargeStatsService;

    /** 费用汇总: 总费用、今日费用、项目明细数、申请单数 */
    @GetMapping
    public R<Map<String, Object>> summary(
            @RequestParam(required = false) String startDate,
            @RequestParam(required = false) String endDate,
            @RequestParam(required = false) String department) {
        return R.ok(chargeStatsService.getChargeSummary(startDate, endDate, department));
    }

    /** 按日统计费用 */
    @GetMapping("/by-day")
    public R<List<Map<String, Object>>> byDay(
            @RequestParam(required = false) String startDate,
            @RequestParam(required = false) String endDate,
            @RequestParam(required = false) String department) {
        return R.ok(chargeStatsService.getChargeByDay(startDate, endDate, department));
    }

    /** 按科室统计费用 */
    @GetMapping("/by-dept")
    public R<List<Map<String, Object>>> byDept(
            @RequestParam(required = false) String startDate,
            @RequestParam(required = false) String endDate) {
        return R.ok(chargeStatsService.getChargeByDept(startDate, endDate));
    }

    /** 按项目统计费用 */
    @GetMapping("/by-item")
    public R<List<Map<String, Object>>> byItem(
            @RequestParam(required = false) String startDate,
            @RequestParam(required = false) String endDate,
            @RequestParam(required = false) String department) {
        return R.ok(chargeStatsService.getChargeByItem(startDate, endDate, department));
    }
}
