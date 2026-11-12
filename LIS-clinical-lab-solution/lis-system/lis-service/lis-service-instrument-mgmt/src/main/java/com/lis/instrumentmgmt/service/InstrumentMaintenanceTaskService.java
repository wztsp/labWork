package com.lis.instrumentmgmt.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.IService;
import com.lis.instrumentmgmt.entity.InstrumentMaintenanceTask;

/**
 * 维护任务服务
 */
public interface InstrumentMaintenanceTaskService extends IService<InstrumentMaintenanceTask> {

    Page<InstrumentMaintenanceTask> queryPage(long current, long size, Long instrumentId, Integer status);

    /**
     * 完成任务
     */
    InstrumentMaintenanceTask finishTask(Long taskId, Long operatorId, String content, java.math.BigDecimal cost);
}
