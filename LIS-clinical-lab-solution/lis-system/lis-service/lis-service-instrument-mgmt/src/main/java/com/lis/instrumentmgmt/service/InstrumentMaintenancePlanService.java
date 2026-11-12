package com.lis.instrumentmgmt.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.lis.instrumentmgmt.entity.InstrumentMaintenancePlan;
import com.lis.instrumentmgmt.entity.InstrumentMaintenanceTask;

import java.time.LocalDate;
import java.util.List;

/**
 * 仪器维护计划服务 - 含任务生成
 */
public interface InstrumentMaintenancePlanService extends IService<InstrumentMaintenancePlan> {

    /**
     * 根据仪器ID查询计划
     */
    List<InstrumentMaintenancePlan> listByInstrument(Long instrumentId);

    /**
     * 查询到期任务 (next_date <= 截止日)
     */
    List<InstrumentMaintenancePlan> listDuePlans(LocalDate asOfDate);

    /**
     * 根据计划生成任务 (从 from 到 to 区间)
     */
    List<InstrumentMaintenanceTask> generateTasks(Long planId, LocalDate from, LocalDate to);
}
