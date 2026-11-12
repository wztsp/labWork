package com.lis.base.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.lis.base.entity.CollectionMethod;
import com.lis.base.mapper.CollectionMethodMapper;
import com.lis.base.service.CollectionMethodService;
import com.lis.common.core.exception.BusinessException;
import com.lis.common.core.util.StringUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 采集方式 Service 实现
 */
@Service
@RequiredArgsConstructor
public class CollectionMethodServiceImpl extends ServiceImpl<CollectionMethodMapper, CollectionMethod> implements CollectionMethodService {

    @Override
    public Page<CollectionMethod> queryPage(long current, long size, String keyword, Integer status) {
        LambdaQueryWrapper<CollectionMethod> wrapper = new LambdaQueryWrapper<>();
        if (StringUtil.isNotEmpty(keyword)) {
            wrapper.and(w -> w.like(CollectionMethod::getMethodCode, keyword)
                    .or().like(CollectionMethod::getMethodName, keyword));
        }
        if (status != null) {
            wrapper.eq(CollectionMethod::getStatus, status);
        }
        wrapper.orderByAsc(CollectionMethod::getSortOrder)
               .orderByDesc(CollectionMethod::getCreateTime);
        return this.page(new Page<>(current, size), wrapper);
    }

    @Override
    public CollectionMethod getByCode(String methodCode) {
        LambdaQueryWrapper<CollectionMethod> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(CollectionMethod::getMethodCode, methodCode);
        return this.getOne(wrapper);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public CollectionMethod create(CollectionMethod collectionMethod) {
        if (this.getByCode(collectionMethod.getMethodCode()) != null) {
            throw new BusinessException("采集方式编码已存在: " + collectionMethod.getMethodCode());
        }
        if (collectionMethod.getStatus() == null) {
            collectionMethod.setStatus(1);
        }
        if (collectionMethod.getSortOrder() == null) {
            collectionMethod.setSortOrder(0);
        }
        collectionMethod.setCreateTime(LocalDateTime.now());
        this.save(collectionMethod);
        return collectionMethod;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public CollectionMethod update(CollectionMethod collectionMethod) {
        if (collectionMethod.getId() == null) {
            throw new BusinessException("ID不能为空");
        }
        this.updateById(collectionMethod);
        return collectionMethod;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        this.removeById(id);
    }

    @Override
    public List<CollectionMethod> listAllEnabled() {
        LambdaQueryWrapper<CollectionMethod> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(CollectionMethod::getStatus, 1);
        wrapper.orderByAsc(CollectionMethod::getSortOrder);
        return this.list(wrapper);
    }
}
