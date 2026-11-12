package com.lis.base.controller;

import com.lis.base.service.InstrumentStatusService;
import com.lis.common.core.result.R;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 仪器在线状态 Controller
 */
@RestController
@RequestMapping("/api/base/instrument-status")
@RequiredArgsConstructor
public class InstrumentStatusController {

    private final InstrumentStatusService instrumentStatusService;

    @GetMapping
    public R<List<Map<String, Object>>> list() {
        return R.ok(instrumentStatusService.listStatus());
    }

    @GetMapping("/{id}")
    public R<Map<String, Object>> get(@PathVariable Long id) {
        return R.ok(instrumentStatusService.getStatus(id));
    }

    @PostMapping("/{id}/online")
    public R<Void> online(@PathVariable Long id) {
        instrumentStatusService.online(id);
        return R.ok();
    }

    @PostMapping("/{id}/offline")
    public R<Void> offline(@PathVariable Long id) {
        instrumentStatusService.offline(id);
        return R.ok();
    }

    /**
     * 心跳接口
     */
    @PostMapping("/{id}/heartbeat")
    public R<Void> heartbeat(@PathVariable Long id) {
        instrumentStatusService.heartbeat(id);
        return R.ok();
    }
}
