package com.lis.micro.controller;

import com.lis.common.core.result.R;
import com.lis.micro.entity.MicroReportPrintConfig;
import com.lis.micro.service.MicroReportPrintConfigService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 微生物报告打印模板配置 Controller
 */
@RestController
@RequestMapping("/api/micro/print-config")
@RequiredArgsConstructor
public class MicroReportPrintConfigController {

    private final MicroReportPrintConfigService printConfigService;

    @GetMapping
    public R<List<MicroReportPrintConfig>> list() {
        return R.ok(printConfigService.listAll());
    }

    @GetMapping("/default")
    public R<MicroReportPrintConfig> getDefault() {
        return R.ok(printConfigService.getDefault());
    }

    @GetMapping("/{id}")
    public R<MicroReportPrintConfig> getById(@PathVariable Long id) {
        return R.ok(printConfigService.getById(id));
    }

    @PostMapping
    public R<MicroReportPrintConfig> create(@RequestBody MicroReportPrintConfig config) {
        return R.ok(printConfigService.create(config));
    }

    @PutMapping
    public R<MicroReportPrintConfig> update(@RequestBody MicroReportPrintConfig config) {
        return R.ok(printConfigService.update(config));
    }

    @DeleteMapping("/{id}")
    public R<Void> delete(@PathVariable Long id) {
        printConfigService.delete(id);
        return R.ok();
    }

    @PostMapping("/{id}/set-default")
    public R<Void> setDefault(@PathVariable Long id) {
        printConfigService.setDefault(id);
        return R.ok();
    }
}
