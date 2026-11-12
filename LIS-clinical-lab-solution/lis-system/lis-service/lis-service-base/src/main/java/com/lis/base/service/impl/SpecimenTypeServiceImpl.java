package com.lis.base.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.lis.base.entity.SpecimenType;
import com.lis.base.mapper.SpecimenTypeMapper;
import com.lis.base.service.SpecimenTypeService;
import com.lis.common.core.exception.BusinessException;
import com.lis.common.core.util.StringUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 标本类型 Service 实现
 */
@Service
@RequiredArgsConstructor
public class SpecimenTypeServiceImpl extends ServiceImpl<SpecimenTypeMapper, SpecimenType> implements SpecimenTypeService {

    @Override
    public Page<SpecimenType> queryPage(long current, long size, String keyword, Integer status) {
        LambdaQueryWrapper<SpecimenType> wrapper = new LambdaQueryWrapper<>();
        if (StringUtil.isNotEmpty(keyword)) {
            wrapper.and(w -> w.like(SpecimenType::getTypeCode, keyword)
                    .or().like(SpecimenType::getTypeName, keyword));
        }
        if (status != null) {
            wrapper.eq(SpecimenType::getStatus, status);
        }
        wrapper.orderByAsc(SpecimenType::getSortOrder)
               .orderByDesc(SpecimenType::getCreateTime);
        return this.page(new Page<>(current, size), wrapper);
    }

    @Override
    public SpecimenType getByCode(String typeCode) {
        LambdaQueryWrapper<SpecimenType> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(SpecimenType::getTypeCode, typeCode);
        return this.getOne(wrapper);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public SpecimenType create(SpecimenType specimenType) {
        if (this.getByCode(specimenType.getTypeCode()) != null) {
            throw new BusinessException("类型编码已存在: " + specimenType.getTypeCode());
        }
        if (specimenType.getStatus() == null) {
            specimenType.setStatus(1);
        }
        if (specimenType.getSortOrder() == null) {
            specimenType.setSortOrder(0);
        }
        specimenType.setCreateTime(LocalDateTime.now());
        this.save(specimenType);
        return specimenType;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public SpecimenType update(SpecimenType specimenType) {
        if (specimenType.getId() == null) {
            throw new BusinessException("ID不能为空");
        }
        this.updateById(specimenType);
        return specimenType;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        this.removeById(id);
    }

    @Override
    public List<SpecimenType> listAllEnabled() {
        LambdaQueryWrapper<SpecimenType> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(SpecimenType::getStatus, 1);
        wrapper.orderByAsc(SpecimenType::getSortOrder);
        return this.list(wrapper);
    }
}
