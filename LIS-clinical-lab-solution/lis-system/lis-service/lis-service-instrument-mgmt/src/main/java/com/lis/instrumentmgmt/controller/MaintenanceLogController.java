package com.lis.instrumentmgmt.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.lis.common.core.result.PageResult;
import com.lis.common.core.result.R;
import com.lis.instrumentmgmt.entity.InstrumentMaintenanceLog;
import com.lis.instrumentmgmt.entity.InstrumentMaintenanceLogItem;
import com.lis.instrumentmgmt.service.InstrumentMaintenanceLogService;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * 仪器维护记录 Controller
 */
@RestController
@RequestMapping("/api/instrument-mgmt/logs")
@RequiredArgsConstructor
public class MaintenanceLogController {

    private final InstrumentMaintenanceLogService logService;

    @GetMapping
    public R<PageResult<InstrumentMaintenanceLog>> page(@RequestParam(defaultValue = "1") long current,
                                                       @RequestParam(defaultValue = "10") long size,
                                                       @RequestParam(required = false) Long instrumentId,
                                                       @RequestParam(required = false) String maintenanceType,
                                                       @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
                                                       @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {
        Page<InstrumentMaintenanceLog> page = logService.queryPage(current, size, instrumentId, maintenanceType, startDate, endDate);
        return R.ok(new PageResult<>(page.getRecords(), page.getTotal(), page.getCurrent(), page.getSize()));
    }

    @GetMapping("/{id}")
    public R<InstrumentMaintenanceLog> getById(@PathVariable Long id) {
        return R.ok(logService.getById(id));
    }

    @GetMapping("/{id}/items")
    public R<List<InstrumentMaintenanceLogItem>> listItems(@PathVariable Long id) {
        return R.ok(logService.listItems(id));
    }

    /**
     * 保存维护记录 (含明细)
     * body: { "log": {...}, "items": [{...}, ...] }
     */
    @PostMapping("/with-items")
    public R<InstrumentMaintenanceLog> saveWithItems(@RequestBody Map<String, Object> body) {
        // 简化: 直接将map转为log和items - 实际项目可用DTO
        com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
        mapper.findAndRegisterModules();
        InstrumentMaintenanceLog log = mapper.convertValue(body.get("log"), InstrumentMaintenanceLog.class);
        List<InstrumentMaintenanceLogItem> items = mapper.convertValue(body.get("items"),
                mapper.getTypeFactory().constructCollectionType(List.class, InstrumentMaintenanceLogItem.class));
        return R.ok(logService.saveWithItems(log, items));
    }

    @PostMapping
    public R<InstrumentMaintenanceLog> create(@RequestBody InstrumentMaintenanceLog log) {
        return R.ok(logService.saveWithItems(log, null));
    }

    @PutMapping
    public R<InstrumentMaintenanceLog> update(@RequestBody InstrumentMaintenanceLog log) {
        logService.updateById(log);
        return R.ok(log);
    }

    @DeleteMapping("/{id}")
    public R<Void> delete(@PathVariable Long id) {
        logService.removeById(id);
        return R.ok();
    }
}
