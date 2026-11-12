package com.lis.instrumentmgmt.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.lis.instrumentmgmt.entity.InstrumentMaintenancePlan;
import com.lis.instrumentmgmt.entity.InstrumentMaintenanceTask;
import com.lis.instrumentmgmt.mapper.InstrumentMaintenancePlanMapper;
import com.lis.instrumentmgmt.mapper.InstrumentMaintenanceTaskMapper;
import com.lis.instrumentmgmt.service.InstrumentMaintenancePlanService;
import com.lis.common.core.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class InstrumentMaintenancePlanServiceImpl extends ServiceImpl<InstrumentMaintenancePlanMapper, InstrumentMaintenancePlan>
        implements InstrumentMaintenancePlanService {

    private final InstrumentMaintenanceTaskMapper taskMapper;

    @Override
    public List<InstrumentMaintenancePlan> listByInstrument(Long instrumentId) {
        return this.list(new LambdaQueryWrapper<InstrumentMaintenancePlan>()
                .eq(InstrumentMaintenancePlan::getInstrumentId, instrumentId)
                .orderByAsc(InstrumentMaintenancePlan::getNextDate));
    }

    @Override
    public List<InstrumentMaintenancePlan> listDuePlans(LocalDate asOfDate) {
        return this.list(new LambdaQueryWrapper<InstrumentMaintenancePlan>()
                .le(InstrumentMaintenancePlan::getNextDate, asOfDate)
                .eq(InstrumentMaintenancePlan::getStatus, 1));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public List<InstrumentMaintenanceTask> generateTasks(Long planId, LocalDate from, LocalDate to) {
        InstrumentMaintenancePlan plan = this.getById(planId);
        if (plan == null) {
            throw new BusinessException("维护计划不存在: " + planId);
        }
        List<InstrumentMaintenanceTask> tasks = new ArrayList<>();
        LocalDate cursor = plan.getFirstDate() != null ? plan.getFirstDate() : from;
        // 如果首次日期早于 from, 推进到 >= from
        while (cursor.isBefore(from)) {
            cursor = advanceByCycle(cursor, plan.getCycleType());
        }
        while (!cursor.isAfter(to)) {
            InstrumentMaintenanceTask task = new InstrumentMaintenanceTask();
            task.setPlanId(planId);
            task.setInstrumentId(plan.getInstrumentId());
            task.setPlanType(plan.getPlanType());
            task.setScheduledDate(cursor);
            task.setStatus(0);
            taskMapper.insert(task);
            tasks.add(task);
            // 推进下一周期
            LocalDate next = advanceByCycle(cursor, plan.getCycleType());
            plan.setNextDate(next);
            this.updateById(plan);
            cursor = next;
        }
        return tasks;
    }

    private LocalDate advanceByCycle(LocalDate date, String cycle) {
        if (cycle == null) return date.plusMonths(1);
        return switch (cycle) {
            case "DAILY" -> date.plusDays(1);
            case "WEEKLY" -> date.plusWeeks(1);
            case "MONTHLY" -> date.plusMonths(1);
            case "QUARTERLY" -> date.plusMonths(3);
            case "YEARLY" -> date.plusYears(1);
            default -> date.plusMonths(1);
        };
    }

    /**
     * 到期提醒: 每日 8 点检查到期计划并生成任务
     * 对 next_date <= 今天的启用计划, 生成当日待执行任务并推进下一周期
     */
    @Scheduled(cron = "0 0 8 * * ?")
    public void checkAndGenerateDueTasks() {
        LocalDate today = LocalDate.now();
        List<InstrumentMaintenancePlan> duePlans = listDuePlans(today);
        if (duePlans.isEmpty()) {
            log.info("到期提醒: 无到期保养计划 (截至 {})", today);
            return;
        }
        int total = 0;
        for (InstrumentMaintenancePlan plan : duePlans) {
            try {
                List<InstrumentMaintenanceTask> tasks = generateTasks(plan.getId(), today, today);
                total += tasks.size();
            } catch (Exception e) {
                log.error("到期提醒: 计划 [{}] 生成任务失败", plan.getId(), e);
            }
        }
        log.info("到期提醒: 检查 {} 个到期计划, 生成 {} 个任务", duePlans.size(), total);
    }
}
