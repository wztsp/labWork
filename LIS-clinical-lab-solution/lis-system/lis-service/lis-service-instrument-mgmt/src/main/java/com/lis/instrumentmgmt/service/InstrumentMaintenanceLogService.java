package com.lis.instrumentmgmt.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.IService;
import com.lis.instrumentmgmt.entity.InstrumentMaintenanceLog;
import com.lis.instrumentmgmt.entity.InstrumentMaintenanceLogItem;

import java.time.LocalDate;
import java.util.List;

/**
 * 仪器维护记录服务
 */
public interface InstrumentMaintenanceLogService extends IService<InstrumentMaintenanceLog> {

    /**
     * 分页查询维护记录
     */
    Page<InstrumentMaintenanceLog> queryPage(long current, long size, Long instrumentId,
                                            String maintenanceType, LocalDate startDate, LocalDate endDate);

    /**
     * 保存维护记录 (含明细)
     */
    InstrumentMaintenanceLog saveWithItems(InstrumentMaintenanceLog log, List<InstrumentMaintenanceLogItem> items);

    /**
     * 查询维护记录明细
     */
    List<InstrumentMaintenanceLogItem> listItems(Long logId);
}
