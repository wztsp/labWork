package com.lis.hr.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.lis.common.core.exception.BusinessException;
import com.lis.hr.entity.StaffTitle;
import com.lis.hr.mapper.StaffTitleMapper;
import com.lis.hr.service.StaffTitleService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class StaffTitleServiceImpl extends ServiceImpl<StaffTitleMapper, StaffTitle> implements StaffTitleService {

    @Override
    public List<StaffTitle> listByStaffId(Long staffId) {
        return this.list(new LambdaQueryWrapper<StaffTitle>()
                .eq(StaffTitle::getStaffId, staffId)
                .orderByDesc(StaffTitle::getAcquireDate));
    }

    @Override
    public StaffTitle getCurrentTitle(Long staffId) {
        return this.getOne(new LambdaQueryWrapper<StaffTitle>()
                .eq(StaffTitle::getStaffId, staffId)
                .eq(StaffTitle::getIsCurrent, 1)
                .last("LIMIT 1"));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public StaffTitle create(StaffTitle staffTitle) {
        if (staffTitle.getStaffId() == null) {
            throw new BusinessException("人员ID不能为空");
        }
        staffTitle.setCreateTime(LocalDateTime.now());
        if (staffTitle.getIsCurrent() == null) {
            staffTitle.setIsCurrent(0);
        }
        // 若设为当前职称，清除其它当前职称
        if (Integer.valueOf(1).equals(staffTitle.getIsCurrent())) {
            this.update(new LambdaUpdateWrapper<StaffTitle>()
                    .eq(StaffTitle::getStaffId, staffTitle.getStaffId())
                    .eq(StaffTitle::getIsCurrent, 1)
                    .set(StaffTitle::getIsCurrent, 0));
        }
        this.save(staffTitle);
        return staffTitle;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public StaffTitle update(StaffTitle staffTitle) {
        if (staffTitle.getId() == null) {
            throw new BusinessException("ID不能为空");
        }
        this.updateById(staffTitle);
        return staffTitle;
    }

    @Override
    public void delete(Long id) {
        this.removeById(id);
    }
}
