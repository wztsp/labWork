package com.lis.base.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.lis.base.entity.InstrumentRepair;
import com.lis.base.mapper.InstrumentRepairMapper;
import com.lis.base.service.InstrumentRepairService;
import com.lis.common.core.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
 * 仪器维修记录 Service 实现
 */
@Service
@RequiredArgsConstructor
public class InstrumentRepairServiceImpl extends ServiceImpl<InstrumentRepairMapper, InstrumentRepair> implements InstrumentRepairService {

    @Override
    public Page<InstrumentRepair> queryPage(long current, long size, Long instrumentId) {
        LambdaQueryWrapper<InstrumentRepair> wrapper = new LambdaQueryWrapper<>();
        if (instrumentId != null) {
            wrapper.eq(InstrumentRepair::getInstrumentId, instrumentId);
        }
        wrapper.orderByDesc(InstrumentRepair::getFaultTime);
        return this.page(new Page<>(current, size), wrapper);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public InstrumentRepair create(InstrumentRepair repair) {
        LocalDateTime now = LocalDateTime.now();
        repair.setCreateTime(now);
        repair.setUpdateTime(now);
        this.save(repair);
        return repair;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public InstrumentRepair update(InstrumentRepair repair) {
        if (repair.getId() == null) {
            throw new BusinessException("维修记录ID不能为空");
        }
        repair.setUpdateTime(LocalDateTime.now());
        this.updateById(repair);
        return repair;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        this.removeById(id);
    }
}
