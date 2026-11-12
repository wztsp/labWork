package com.lis.base.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.IService;
import com.lis.base.entity.InstrumentCalibration;

/**
 * 仪器校准记录 Service
 */
public interface InstrumentCalibrationService extends IService<InstrumentCalibration> {

    /**
     * 分页查询校准记录
     */
    Page<InstrumentCalibration> queryPage(long current, long size, Long instrumentId);

    /**
     * 新增
     */
    InstrumentCalibration create(InstrumentCalibration calibration);

    /**
     * 修改
     */
    InstrumentCalibration update(InstrumentCalibration calibration);

    /**
     * 删除
     */
    void delete(Long id);
}
