package com.lis.stats.controller;

import com.lis.common.core.result.R;
import com.lis.stats.service.TrendAnalysisService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 阳性率统计 Controller
 */
@RestController
@RequestMapping("/api/stats/positive-rate")
@RequiredArgsConstructor
public class PositiveRateController {

    private final TrendAnalysisService trendAnalysisService;

    /** 按项目/时间维度统计阳性率 (可选标本类型过滤) */
    @GetMapping
    public R<List<Map<String, Object>>> positiveRate(
            @RequestParam(required = false) String startDate,
            @RequestParam(required = false) String endDate,
            @RequestParam(required = false) Long testItemId,
            @RequestParam(required = false) String specimenType) {
        return R.ok(trendAnalysisService.getPositiveRate(startDate, endDate, testItemId, specimenType));
    }

    /** 阳性率趋势 (按日期分组, 用于折线图) */
    @GetMapping("/trend")
    public R<List<Map<String, Object>>> positiveRateTrend(
            @RequestParam(required = false) String startDate,
            @RequestParam(required = false) String endDate,
            @RequestParam(required = false) Long testItemId,
            @RequestParam(required = false) String specimenType) {
        return R.ok(trendAnalysisService.getPositiveRateTrend(startDate, endDate, testItemId, specimenType));
    }
}
