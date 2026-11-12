package com.lis.micro.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.lis.micro.entity.MediumType;
import com.lis.micro.mapper.MediumTypeMapper;
import com.lis.micro.service.MediumTypeService;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 培养基类型字典服务实现
 */
@Service
public class MediumTypeServiceImpl extends ServiceImpl<MediumTypeMapper, MediumType> implements MediumTypeService {

    @Override
    public Page<MediumType> queryPage(long current, long size, String keyword) {
        LambdaQueryWrapper<MediumType> wrapper = new LambdaQueryWrapper<>();
        if (keyword != null && !keyword.isEmpty()) {
            wrapper.and(w -> w.like(MediumType::getMediumCode, keyword)
                    .or().like(MediumType::getMediumName, keyword)
                    .or().like(MediumType::getMediumAbbr, keyword));
        }
        wrapper.orderByDesc(MediumType::getStatus).orderByAsc(MediumType::getMediumCode);
        return this.page(new Page<>(current, size), wrapper);
    }

    @Override
    public List<MediumType> listEnabled() {
        return this.list(new LambdaQueryWrapper<MediumType>()
                .eq(MediumType::getStatus, 1)
                .orderByAsc(MediumType::getMediumCode));
    }
}