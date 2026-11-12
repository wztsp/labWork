package com.lis.report.controller;

import com.lis.api.vo.MicroEpiStatVO;
import com.lis.common.core.result.R;
import com.lis.report.service.MicroEpiStatService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 微生物流行病学统计 Controller
 */
@RestController
@RequestMapping("/api/report/micro/epi-stat")
@RequiredArgsConstructor
public class MicroEpiStatController {

    private final MicroEpiStatService microEpiStatService;

    /** 汇总统计 (支持按时间范围过滤) */
    @GetMapping
    public R<MicroEpiStatVO> statistics(
            @RequestParam(required = false) String beginDate,
            @RequestParam(required = false) String endDate) {
        return R.ok(microEpiStatService.statistics(beginDate, endDate));
    }
}
