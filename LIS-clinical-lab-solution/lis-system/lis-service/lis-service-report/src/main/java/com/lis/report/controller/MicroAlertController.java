package com.lis.report.controller;

import com.lis.common.core.result.R;
import com.lis.report.entity.MicroAlert;
import com.lis.report.service.MicroAlertService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 微生物特殊耐药/危急值预警 Controller
 */
@RestController
@RequestMapping("/api/report/micro/alerts")
@RequiredArgsConstructor
public class MicroAlertController {

    private final MicroAlertService microAlertService;

    @GetMapping
    public R<List<MicroAlert>> list(@RequestParam(required = false) String status,
                                    @RequestParam(required = false) String alertLevel) {
        return R.ok(microAlertService.listAll(status, alertLevel));
    }

    @GetMapping("/report/{reportId}")
    public R<List<MicroAlert>> listByReport(@PathVariable Long reportId) {
        return R.ok(microAlertService.listByReportId(reportId));
    }

    @PostMapping("/{id}/read")
    public R<Void> markRead(@PathVariable Long id) {
        microAlertService.markRead(id);
        return R.ok();
    }

    @PostMapping("/{id}/handle")
    public R<Void> handle(@PathVariable Long id,
                         @RequestParam String handler,
                         @RequestParam(required = false) String remark) {
        microAlertService.handle(id, handler, remark);
        return R.ok();
    }
}
