package com.lis.base.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.IService;
import com.lis.base.entity.InstrumentRepair;

/**
 * 仪器维修记录 Service
 */
public interface InstrumentRepairService extends IService<InstrumentRepair> {

    /**
     * 分页查询维修记录
     */
    Page<InstrumentRepair> queryPage(long current, long size, Long instrumentId);

    /**
     * 新增
     */
    InstrumentRepair create(InstrumentRepair repair);

    /**
     * 修改
     */
    InstrumentRepair update(InstrumentRepair repair);

    /**
     * 删除
     */
    void delete(Long id);
}
