package com.lis.instrumentmgmt.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.lis.common.core.exception.BusinessException;
import com.lis.instrumentmgmt.entity.InstrumentMaintenancePlan;
import com.lis.instrumentmgmt.entity.InstrumentMaintenanceTask;
import com.lis.instrumentmgmt.mapper.InstrumentMaintenanceTaskMapper;
import com.lis.instrumentmgmt.service.InstrumentMaintenancePlanService;
import com.lis.instrumentmgmt.service.InstrumentMaintenanceTaskService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;

@Service
@RequiredArgsConstructor
public class InstrumentMaintenanceTaskServiceImpl extends ServiceImpl<InstrumentMaintenanceTaskMapper, InstrumentMaintenanceTask>
        implements InstrumentMaintenanceTaskService {

    private final InstrumentMaintenancePlanService planService;

    @Override
    public Page<InstrumentMaintenanceTask> queryPage(long current, long size, Long instrumentId, Integer status) {
        LambdaQueryWrapper<InstrumentMaintenanceTask> wrapper = new LambdaQueryWrapper<>();
        if (instrumentId != null) {
            wrapper.eq(InstrumentMaintenanceTask::getInstrumentId, instrumentId);
        }
        if (status != null) {
            wrapper.eq(InstrumentMaintenanceTask::getStatus, status);
        }
        wrapper.orderByAsc(InstrumentMaintenanceTask::getScheduledDate);
        return this.page(new Page<>(current, size), wrapper);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public InstrumentMaintenanceTask finishTask(Long taskId, Long operatorId, String content, BigDecimal cost) {
        InstrumentMaintenanceTask task = this.getById(taskId);
        if (task == null) {
            throw new BusinessException("维护任务不存在: " + taskId);
        }
        task.setOperatorId(operatorId);
        task.setContent(content);
        task.setCost(cost);
        task.setFinishDate(LocalDate.now());
        task.setStatus(1);
        this.updateById(task);
        // 推进计划下次执行日期
        if (task.getPlanId() != null) {
            InstrumentMaintenancePlan plan = planService.getById(task.getPlanId());
            if (plan != null) {
                plan.setNextDate(task.getScheduledDate().plusMonths(1));
                planService.updateById(plan);
            }
        }
        return task;
    }
}
