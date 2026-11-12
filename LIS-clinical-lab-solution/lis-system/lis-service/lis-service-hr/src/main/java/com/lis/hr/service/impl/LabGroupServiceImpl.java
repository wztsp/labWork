package com.lis.hr.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.lis.common.core.exception.BusinessException;
import com.lis.hr.entity.LabGroup;
import com.lis.hr.mapper.LabGroupMapper;
import com.lis.hr.service.LabGroupService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class LabGroupServiceImpl extends ServiceImpl<LabGroupMapper, LabGroup> implements LabGroupService {

    @Override
    public List<LabGroup> listAll() {
        return this.list(new LambdaQueryWrapper<LabGroup>()
                .orderByAsc(LabGroup::getSortOrder));
    }

    @Override
    public List<LabGroup> listTree() {
        // 简单实现：返回全部，由前端构建树
        return listAll();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public LabGroup create(LabGroup group) {
        if (group.getGroupCode() == null || group.getGroupCode().isEmpty()) {
            throw new BusinessException("小组编码不能为空");
        }
        LambdaQueryWrapper<LabGroup> wrapper = new LambdaQueryWrapper<LabGroup>()
                .eq(LabGroup::getGroupCode, group.getGroupCode());
        if (this.count(wrapper) > 0) {
            throw new BusinessException("小组编码已存在: " + group.getGroupCode());
        }
        if (group.getStatus() == null) {
            group.setStatus(1);
        }
        if (group.getSortOrder() == null) {
            group.setSortOrder(0);
        }
        if (group.getParentId() == null) {
            group.setParentId(0L);
        }
        group.setCreateTime(LocalDateTime.now());
        this.save(group);
        return group;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public LabGroup update(LabGroup group) {
        if (group.getId() == null) {
            throw new BusinessException("ID不能为空");
        }
        this.updateById(group);
        return group;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        this.removeById(id);
    }
}
