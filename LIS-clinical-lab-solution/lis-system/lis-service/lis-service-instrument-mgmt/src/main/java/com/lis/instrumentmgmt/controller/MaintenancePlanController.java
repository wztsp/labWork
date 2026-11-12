package com.lis.instrumentmgmt.controller;

import com.lis.common.core.result.R;
import com.lis.instrumentmgmt.entity.InstrumentMaintenancePlan;
import com.lis.instrumentmgmt.entity.InstrumentMaintenanceTask;
import com.lis.instrumentmgmt.service.InstrumentMaintenancePlanService;
import com.lis.instrumentmgmt.service.InstrumentMaintenanceTaskService;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

/**
 * 仪器维护计划与任务 Controller
 */
@RestController
@RequestMapping("/api/instrument-mgmt/plans")
@RequiredArgsConstructor
public class MaintenancePlanController {

    private final InstrumentMaintenancePlanService planService;
    private final InstrumentMaintenanceTaskService taskService;

    /** 按仪器列出计划 */
    @GetMapping("/instrument/{instrumentId}")
    public R<List<InstrumentMaintenancePlan>> listByInstrument(@PathVariable Long instrumentId) {
        return R.ok(planService.listByInstrument(instrumentId));
    }

    /** 列出到期计划 */
    @GetMapping("/due")
    public R<List<InstrumentMaintenancePlan>> listDue(@RequestParam(required = false)
                                                      @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate asOfDate) {
        return R.ok(planService.listDuePlans(asOfDate == null ? LocalDate.now() : asOfDate));
    }

    @GetMapping("/{id}")
    public R<InstrumentMaintenancePlan> getById(@PathVariable Long id) {
        return R.ok(planService.getById(id));
    }

    @PostMapping
    public R<InstrumentMaintenancePlan> create(@RequestBody InstrumentMaintenancePlan plan) {
        planService.save(plan);
        return R.ok(plan);
    }

    @PutMapping
    public R<InstrumentMaintenancePlan> update(@RequestBody InstrumentMaintenancePlan plan) {
        planService.updateById(plan);
        return R.ok(plan);
    }

    @DeleteMapping("/{id}")
    public R<Void> delete(@PathVariable Long id) {
        planService.removeById(id);
        return R.ok();
    }

    /** 根据计划生成任务 (从 from 到 to 区间) */
    @PostMapping("/{planId}/generate-tasks")
    public R<List<InstrumentMaintenanceTask>> generateTasks(@PathVariable Long planId,
                                                            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                                                            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return R.ok(planService.generateTasks(planId, from, to));
    }

    // ============ 任务管理 ============

    @GetMapping("/tasks")
    public R<List<InstrumentMaintenanceTask>> listTasks(@RequestParam(required = false) Long instrumentId,
                                                       @RequestParam(required = false) Integer status) {
        return R.ok(taskService.list(new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<InstrumentMaintenanceTask>()
                .eq(instrumentId != null, InstrumentMaintenanceTask::getInstrumentId, instrumentId)
                .eq(status != null, InstrumentMaintenanceTask::getStatus, status)
                .orderByAsc(InstrumentMaintenanceTask::getScheduledDate)));
    }

    /** 完成任务 */
    @PostMapping("/tasks/{taskId}/finish")
    public R<InstrumentMaintenanceTask> finishTask(@PathVariable Long taskId,
                                                   @RequestParam(required = false) Long operatorId,
                                                   @RequestParam(required = false) String content,
                                                   @RequestParam(required = false) java.math.BigDecimal cost) {
        return R.ok(taskService.finishTask(taskId, operatorId, content, cost));
    }
}
