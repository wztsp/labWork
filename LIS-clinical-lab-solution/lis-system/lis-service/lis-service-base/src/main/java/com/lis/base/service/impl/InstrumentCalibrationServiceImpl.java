package com.lis.base.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.lis.base.entity.InstrumentCalibration;
import com.lis.base.mapper.InstrumentCalibrationMapper;
import com.lis.base.service.InstrumentCalibrationService;
import com.lis.common.core.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
 * 仪器校准记录 Service 实现
 */
@Service
@RequiredArgsConstructor
public class InstrumentCalibrationServiceImpl extends ServiceImpl<InstrumentCalibrationMapper, InstrumentCalibration> implements InstrumentCalibrationService {

    @Override
    public Page<InstrumentCalibration> queryPage(long current, long size, Long instrumentId) {
        LambdaQueryWrapper<InstrumentCalibration> wrapper = new LambdaQueryWrapper<>();
        if (instrumentId != null) {
            wrapper.eq(InstrumentCalibration::getInstrumentId, instrumentId);
        }
        wrapper.orderByDesc(InstrumentCalibration::getCalibrationTime);
        return this.page(new Page<>(current, size), wrapper);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public InstrumentCalibration create(InstrumentCalibration calibration) {
        LocalDateTime now = LocalDateTime.now();
        calibration.setCreateTime(now);
        calibration.setUpdateTime(now);
        this.save(calibration);
        return calibration;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public InstrumentCalibration update(InstrumentCalibration calibration) {
        if (calibration.getId() == null) {
            throw new BusinessException("校准记录ID不能为空");
        }
        calibration.setUpdateTime(LocalDateTime.now());
        this.updateById(calibration);
        return calibration;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        this.removeById(id);
    }
}
