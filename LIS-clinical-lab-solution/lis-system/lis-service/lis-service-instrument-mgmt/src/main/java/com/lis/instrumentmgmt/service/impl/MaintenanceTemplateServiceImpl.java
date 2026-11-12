package com.lis.instrumentmgmt.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.lis.instrumentmgmt.entity.MaintenanceTemplate;
import com.lis.instrumentmgmt.entity.MaintenanceTemplateItem;
import com.lis.instrumentmgmt.mapper.MaintenanceTemplateItemMapper;
import com.lis.instrumentmgmt.mapper.MaintenanceTemplateMapper;
import com.lis.instrumentmgmt.service.MaintenanceTemplateService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class MaintenanceTemplateServiceImpl extends ServiceImpl<MaintenanceTemplateMapper, MaintenanceTemplate>
        implements MaintenanceTemplateService {

    private final MaintenanceTemplateItemMapper itemMapper;

    @Override
    public Page<MaintenanceTemplate> queryPage(long current, long size, String keyword, String cycleType) {
        LambdaQueryWrapper<MaintenanceTemplate> wrapper = new LambdaQueryWrapper<>();
        if (keyword != null && !keyword.isEmpty()) {
            wrapper.and(w -> w.like(MaintenanceTemplate::getTemplateCode, keyword)
                    .or().like(MaintenanceTemplate::getTemplateName, keyword));
        }
        if (cycleType != null && !cycleType.isEmpty()) {
            wrapper.eq(MaintenanceTemplate::getCycleType, cycleType);
        }
        wrapper.orderByDesc(MaintenanceTemplate::getCreateTime);
        return this.page(new Page<>(current, size), wrapper);
    }

    @Override
    public MaintenanceTemplate getByCode(String templateCode) {
        return this.getOne(new LambdaQueryWrapper<MaintenanceTemplate>()
                .eq(MaintenanceTemplate::getTemplateCode, templateCode));
    }

    @Override
    public List<MaintenanceTemplateItem> listItems(Long templateId) {
        return itemMapper.selectList(new LambdaQueryWrapper<MaintenanceTemplateItem>()
                .eq(MaintenanceTemplateItem::getTemplateId, templateId)
                .orderByAsc(MaintenanceTemplateItem::getSortOrder));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void saveItems(Long templateId, List<MaintenanceTemplateItem> items) {
        itemMapper.delete(new LambdaQueryWrapper<MaintenanceTemplateItem>()
                .eq(MaintenanceTemplateItem::getTemplateId, templateId));
        if (items != null && !items.isEmpty()) {
            items.forEach(item -> {
                item.setId(null);
                item.setTemplateId(templateId);
                itemMapper.insert(item);
            });
        }
    }
}
