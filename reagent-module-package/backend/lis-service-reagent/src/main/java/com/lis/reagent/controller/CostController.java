package com.lis.reagent.controller;

import com.lis.common.core.result.R;
import com.lis.reagent.mapper.ReagentCostMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 试剂成本核算 Controller
 */
@RestController
@RequestMapping("/api/reagent/cost")
@RequiredArgsConstructor
public class CostController {

    private final ReagentCostMapper costMapper;

    /**
     * 成本汇总 (按时间段, 按维度)
     * @param dimension reagent(项目维度,默认) / instrument(仪器维度)
     */
    @GetMapping("/summary")
    public R<List<Map<String, Object>>> summary(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate start,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate end,
            @RequestParam(defaultValue = "reagent") String dimension) {
        LocalDate startDate = start != null ? start : LocalDate.now().minusMonths(1);
        LocalDate endDate = end != null ? end.plusDays(1) : LocalDate.now().plusDays(1);
        LocalDateTime startTime = startDate.atStartOfDay();
        LocalDateTime endTime = endDate.atStartOfDay();
        return R.ok(costMapper.summary(startTime, endTime, dimension));
    }

    /**
     * 成本明细列表
     */
    @GetMapping("/detail")
    public R<List<Map<String, Object>>> detail(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate start,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate end) {
        LocalDate startDate = start != null ? start : LocalDate.now().minusMonths(1);
        LocalDate endDate = end != null ? end.plusDays(1) : LocalDate.now().plusDays(1);
        LocalDateTime startTime = startDate.atStartOfDay();
        LocalDateTime endTime = endDate.atStartOfDay();
        return R.ok(costMapper.detail(startTime, endTime));
    }
}
