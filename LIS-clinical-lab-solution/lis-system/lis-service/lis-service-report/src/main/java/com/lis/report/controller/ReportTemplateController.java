package com.lis.report.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.lis.common.core.result.R;
import com.lis.report.entity.ReportTemplate;
import com.lis.report.service.ReportTemplateService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/report/templates")
@RequiredArgsConstructor
public class ReportTemplateController {

    private final ReportTemplateService reportTemplateService;

    @GetMapping
    public R<Page<ReportTemplate>> page(
            @RequestParam(defaultValue = "1") int current,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String name,
            @RequestParam(required = false) Long instrumentId,
            @RequestParam(required = false) Integer reportType) {
        return R.ok(reportTemplateService.queryPage(current, size, name, instrumentId, reportType));
    }

    @GetMapping("/list")
    public R<List<ReportTemplate>> list() {
        return R.ok(reportTemplateService.listAll());
    }

    @GetMapping("/{id}")
    public R<ReportTemplate> get(@PathVariable Long id) {
        return R.ok(reportTemplateService.getById(id));
    }

    @GetMapping("/by-instrument")
    public R<ReportTemplate> getByInstrument(
            @RequestParam(required = false) Long instrumentId,
            @RequestParam(required = false) Integer reportType) {
        if (reportType != null) {
            return R.ok(reportTemplateService.getByInstrumentAndType(instrumentId, reportType));
        }
        return R.ok(reportTemplateService.getByInstrumentId(instrumentId));
    }

    @GetMapping("/default")
    public R<ReportTemplate> getDefault(@RequestParam(required = false) Integer reportType) {
        if (reportType != null) {
            return R.ok(reportTemplateService.getDefaultByType(reportType));
        }
        return R.ok(reportTemplateService.getDefault());
    }

    @PostMapping
    public R<ReportTemplate> create(@RequestBody ReportTemplate template) {
        return R.ok(reportTemplateService.create(template));
    }

    @PutMapping
    public R<ReportTemplate> update(@RequestBody ReportTemplate template) {
        return R.ok(reportTemplateService.update(template));
    }

    @DeleteMapping("/{id}")
    public R<Void> delete(@PathVariable Long id) {
        reportTemplateService.delete(id);
        return R.ok();
    }

    @PutMapping("/{id}/default")
    public R<Void> setDefault(@PathVariable Long id) {
        reportTemplateService.setDefault(id);
        return R.ok();
    }
}
