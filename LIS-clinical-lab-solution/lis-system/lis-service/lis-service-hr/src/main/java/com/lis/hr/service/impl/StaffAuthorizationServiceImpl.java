package com.lis.hr.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.lis.common.core.exception.BusinessException;
import com.lis.hr.entity.StaffAuthorization;
import com.lis.hr.mapper.StaffAuthorizationMapper;
import com.lis.hr.service.StaffAuthorizationService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Service
public class StaffAuthorizationServiceImpl extends ServiceImpl<StaffAuthorizationMapper, StaffAuthorization>
        implements StaffAuthorizationService {

    @Override
    public List<StaffAuthorization> listByStaffId(Long staffId) {
        return this.list(new LambdaQueryWrapper<StaffAuthorization>()
                .eq(StaffAuthorization::getStaffId, staffId)
                .orderByDesc(StaffAuthorization::getStartDate));
    }

    @Override
    public boolean hasAuthorization(Long staffId, String authType, String scope) {
        LocalDate today = LocalDate.now();
        LambdaQueryWrapper<StaffAuthorization> wrapper = new LambdaQueryWrapper<StaffAuthorization>()
                .eq(StaffAuthorization::getStaffId, staffId)
                .eq(StaffAuthorization::getAuthType, authType)
                .eq(StaffAuthorization::getStatus, 1)
                .le(StaffAuthorization::getStartDate, today)
                .ge(StaffAuthorization::getEndDate, today);
        if (scope != null && !scope.isEmpty()) {
            wrapper.like(StaffAuthorization::getAuthScope, scope);
        }
        return this.count(wrapper) > 0;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public StaffAuthorization create(StaffAuthorization authorization) {
        if (authorization.getStaffId() == null) {
            throw new BusinessException("人员ID不能为空");
        }
        if (authorization.getAuthType() == null) {
            throw new BusinessException("授权类型不能为空");
        }
        if (authorization.getStatus() == null) {
            authorization.setStatus(1);
        }
        authorization.setCreateTime(LocalDateTime.now());
        this.save(authorization);
        return authorization;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public StaffAuthorization update(StaffAuthorization authorization) {
        if (authorization.getId() == null) {
            throw new BusinessException("ID不能为空");
        }
        this.updateById(authorization);
        return authorization;
    }

    @Override
    public void delete(Long id) {
        this.removeById(id);
    }
}
