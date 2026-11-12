package com.lis.reagent.controller;

import com.lis.common.core.result.R;
import com.lis.reagent.entity.ReagentOpening;
import com.lis.reagent.entity.ReagentStock;
import com.lis.reagent.service.ReagentDashboardService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 试剂看板 Controller
 */
@RestController
@RequestMapping("/api/reagent/dashboard")
@RequiredArgsConstructor
public class ReagentDashboardController {

    private final ReagentDashboardService dashboardService;

    @GetMapping("/overview")
    public R<Map<String, Object>> overview() {
        return R.ok(dashboardService.getOverview());
    }

    @GetMapping("/near-expiry")
    public R<List<ReagentStock>> nearExpiry(@RequestParam(defaultValue = "30") int days) {
        return R.ok(dashboardService.getNearExpiryList(days));
    }

    @GetMapping("/low-stock")
    public R<List<Map<String, Object>>> lowStock() {
        return R.ok(dashboardService.getLowStockList());
    }

    @GetMapping("/opened-expiry")
    public R<List<ReagentOpening>> openedExpiry() {
        return R.ok(dashboardService.getOpenedExpiryList());
    }
}
