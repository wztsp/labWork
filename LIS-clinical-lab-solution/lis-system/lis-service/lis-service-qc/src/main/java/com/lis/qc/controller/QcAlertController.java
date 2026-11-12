package com.lis.qc.controller;

import com.lis.common.core.result.PageResult;
import com.lis.common.core.result.R;
import com.lis.qc.entity.QcAlert;
import com.lis.qc.service.QcAlertService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

/**
 * 质控偏差趋势预警 Controller
 */
@RestController
@RequestMapping("/api/qc/alerts")
@RequiredArgsConstructor
public class QcAlertController {

    private final QcAlertService qcAlertService;

    @GetMapping("/page")
    public R<PageResult<QcAlert>> page(
            @RequestParam(defaultValue = "1") int current,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) String status) {
        return R.ok(qcAlertService.queryPage(current, size, status));
    }

    /** 标记预警为已读 */
    @PutMapping("/{id}/read")
    public R<Boolean> markRead(@PathVariable Long id) {
        return R.ok(qcAlertService.markRead(id));
    }
}
