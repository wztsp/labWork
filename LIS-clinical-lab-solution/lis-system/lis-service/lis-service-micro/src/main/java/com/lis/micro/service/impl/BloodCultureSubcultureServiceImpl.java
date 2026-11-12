package com.lis.micro.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.lis.common.core.exception.BusinessException;
import com.lis.micro.entity.BloodCultureSubculture;
import com.lis.micro.mapper.BloodCultureSubcultureMapper;
import com.lis.micro.service.BloodCultureSubcultureService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 血培养转种记录服务实现
 */
@Service
public class BloodCultureSubcultureServiceImpl extends ServiceImpl<BloodCultureSubcultureMapper, BloodCultureSubculture>
        implements BloodCultureSubcultureService {

    @Override
    public List<BloodCultureSubculture> listByBloodCultureId(Long bloodCultureId) {
        return this.list(new LambdaQueryWrapper<BloodCultureSubculture>()
                .eq(BloodCultureSubculture::getBloodCultureId, bloodCultureId)
                .orderByAsc(BloodCultureSubculture::getSubcultureTime));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public BloodCultureSubculture addSubculture(BloodCultureSubculture subculture) {
        if (subculture.getBloodCultureId() == null) {
            throw new BusinessException("血培养ID不能为空");
        }
        if (subculture.getSubcultureTime() == null) {
            subculture.setSubcultureTime(LocalDateTime.now());
        }
        this.save(subculture);
        return subculture;
    }
}
