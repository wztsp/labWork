package com.lis.qc.controller;

import com.lis.common.core.result.R;
import com.lis.qc.service.QCChartService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.Map;

/**
 * 质控图数据 Controller
 */
@RestController
@RequestMapping("/api/qc/charts")
@RequiredArgsConstructor
public class QCChartController {

    private final QCChartService qcChartService;

    @Value("${qc.chart.lj-points:50}")
    private int ljPoints;
    @Value("${qc.chart.zscore-points:50}")
    private int zscorePoints;

    /** Levey-Jennings 图数据 */
    @GetMapping("/lj")
    public R<Map<String, Object>> lj(
            @RequestParam Long lotId,
            @RequestParam(required = false, defaultValue = "0") int limit,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate beginDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {
        int realLimit = limit > 0 ? limit : ljPoints;
        return R.ok(qcChartService.getLeveyJenningsData(lotId, realLimit, beginDate, endDate));
    }

    /** Z-分数图数据 */
    @GetMapping("/z-score")
    public R<Map<String, Object>> zScore(
            @RequestParam Long lotId,
            @RequestParam(required = false, defaultValue = "0") int limit,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate beginDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {
        int realLimit = limit > 0 ? limit : zscorePoints;
        return R.ok(qcChartService.getZScoreData(lotId, realLimit, beginDate, endDate));
    }
}
