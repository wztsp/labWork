package com.lis.monolith.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.lis.common.core.exception.BusinessException;
import com.lis.monolith.entity.InfectiousItem;
import com.lis.monolith.mapper.InfectiousItemMapper;
import com.lis.monolith.service.InfectiousItemService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 传染病项目配置服务实现
 */
@Service
@RequiredArgsConstructor
public class InfectiousItemServiceImpl implements InfectiousItemService {

    private final InfectiousItemMapper infectiousItemMapper;

    @Override
    public IPage<InfectiousItem> page(int page, int size, String keyword) {
        LambdaQueryWrapper<InfectiousItem> qw = new LambdaQueryWrapper<>();
        if (StringUtils.hasText(keyword)) {
            qw.like(InfectiousItem::getTestItemName, keyword);
        }
        qw.orderByDesc(InfectiousItem::getId);
        return infectiousItemMapper.selectPage(new Page<>(Math.max(page, 1), Math.max(size, 1)), qw);
    }

    @Override
    public List<InfectiousItem> listAll() {
        return infectiousItemMapper.selectList(new LambdaQueryWrapper<InfectiousItem>().orderByDesc(InfectiousItem::getId));
    }

    @Override
    public InfectiousItem create(InfectiousItem item) {
        if (item.getTestItemId() == null) {
            throw new BusinessException("检验项目不能为空");
        }
        Long exists = infectiousItemMapper.selectCount(new LambdaQueryWrapper<InfectiousItem>()
                .eq(InfectiousItem::getTestItemId, item.getTestItemId()));
        if (exists != null && exists > 0) {
            throw new BusinessException("该检验项目已配置为传染病项目");
        }
        item.setId(null);
        item.setPositiveText(StringUtils.hasText(item.getPositiveText()) ? item.getPositiveText() : "阳性");
        item.setEnabled(item.getEnabled() == null ? 1 : item.getEnabled());
        item.setCreateTime(LocalDateTime.now());
        infectiousItemMapper.insert(item);
        return item;
    }

    @Override
    public InfectiousItem update(InfectiousItem item) {
        InfectiousItem db = infectiousItemMapper.selectById(item.getId());
        if (db == null) {
            throw new BusinessException("传染病项目配置不存在");
        }
        db.setTestItemId(item.getTestItemId());
        db.setTestItemName(item.getTestItemName());
        db.setPositiveText(item.getPositiveText());
        db.setEnabled(item.getEnabled());
        db.setRemark(item.getRemark());
        infectiousItemMapper.updateById(db);
        return db;
    }

    @Override
    public void delete(Long id) {
        infectiousItemMapper.deleteById(id);
    }
}