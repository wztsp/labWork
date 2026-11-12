package com.lis.qc.controller;

import com.lis.common.core.result.R;
import com.lis.qc.service.QcMonthlyReportService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 质控报告 Controller
 */
@RestController
@RequestMapping("/api/qc/report")
@RequiredArgsConstructor
public class QcReportController {

    private final QcMonthlyReportService qcMonthlyReportService;

    /** 月报 */
    @GetMapping("/monthly")
    public R<Map<String, Object>> monthly(
            @RequestParam int year,
            @RequestParam int month) {
        return R.ok(qcMonthlyReportService.generateMonthlyReport(year, month));
    }
}
