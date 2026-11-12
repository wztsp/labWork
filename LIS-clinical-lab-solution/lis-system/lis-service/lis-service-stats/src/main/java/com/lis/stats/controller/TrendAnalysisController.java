package com.lis.stats.controller;

import com.lis.common.core.result.R;
import com.lis.stats.service.TrendAnalysisService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 趋势分析 Controller
 */
@RestController
@RequestMapping("/api/stats/trend")
@RequiredArgsConstructor
public class TrendAnalysisController {

    private final TrendAnalysisService trendAnalysisService;

    @Value("${stats.trend-default-points:100}")
    private int defaultTrendPoints;

    /** 患者历史结果趋势 (按 patientId + testItemId) */
    @GetMapping
    public R<List<Map<String, Object>>> patientTrend(
            @RequestParam Long patientId,
            @RequestParam Long testItemId,
            @RequestParam(required = false, defaultValue = "0") int limit) {
        int realLimit = limit > 0 ? limit : defaultTrendPoints;
        return R.ok(trendAnalysisService.getPatientTrend(patientId, testItemId, realLimit));
    }

    /** 阳性率统计 (按项目/时间, 可选标本类型) */
    @GetMapping("/positive-rate")
    public R<List<Map<String, Object>>> positiveRate(
            @RequestParam(required = false) String startDate,
            @RequestParam(required = false) String endDate,
            @RequestParam(required = false) Long testItemId,
            @RequestParam(required = false) String specimenType) {
        return R.ok(trendAnalysisService.getPositiveRate(startDate, endDate, testItemId, specimenType));
    }
}
