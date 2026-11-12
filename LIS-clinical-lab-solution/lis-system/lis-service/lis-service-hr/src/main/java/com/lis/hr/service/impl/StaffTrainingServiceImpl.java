package com.lis.hr.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.lis.common.core.exception.BusinessException;
import com.lis.hr.entity.StaffTraining;
import com.lis.hr.mapper.StaffTrainingMapper;
import com.lis.hr.service.StaffTrainingService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class StaffTrainingServiceImpl extends ServiceImpl<StaffTrainingMapper, StaffTraining>
        implements StaffTrainingService {

    @Override
    public List<StaffTraining> listByStaffId(Long staffId) {
        return this.list(new LambdaQueryWrapper<StaffTraining>()
                .eq(StaffTraining::getStaffId, staffId)
                .orderByDesc(StaffTraining::getTrainingDate));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public StaffTraining create(StaffTraining training) {
        if (training.getStaffId() == null) {
            throw new BusinessException("人员ID不能为空");
        }
        training.setCreateTime(LocalDateTime.now());
        this.save(training);
        return training;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public StaffTraining update(StaffTraining training) {
        if (training.getId() == null) {
            throw new BusinessException("ID不能为空");
        }
        this.updateById(training);
        return training;
    }

    @Override
    public void delete(Long id) {
        this.removeById(id);
    }
}
