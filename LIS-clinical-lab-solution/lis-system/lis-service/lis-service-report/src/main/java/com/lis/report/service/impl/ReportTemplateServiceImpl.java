package com.lis.report.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.lis.report.entity.ReportTemplate;
import com.lis.report.mapper.ReportTemplateMapper;
import com.lis.report.service.ReportTemplateService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class ReportTemplateServiceImpl extends ServiceImpl<ReportTemplateMapper, ReportTemplate> implements ReportTemplateService {

    @Override
    public Page<ReportTemplate> queryPage(int current, int size, String name, Long instrumentId, Integer reportType) {
        LambdaQueryWrapper<ReportTemplate> wrapper = new LambdaQueryWrapper<>();
        if (StringUtils.hasText(name)) {
            wrapper.like(ReportTemplate::getTemplateName, name);
        }
        if (instrumentId != null) {
            wrapper.eq(ReportTemplate::getInstrumentId, instrumentId);
        }
        if (reportType != null) {
            wrapper.eq(ReportTemplate::getReportType, reportType);
        }
        wrapper.orderByDesc(ReportTemplate::getIsDefault)
               .orderByDesc(ReportTemplate::getCreateTime);
        return this.page(new Page<>(current, size), wrapper);
    }

    @Override
    public List<ReportTemplate> listAll() {
        LambdaQueryWrapper<ReportTemplate> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(ReportTemplate::getStatus, 1)
               .orderByDesc(ReportTemplate::getIsDefault)
               .orderByDesc(ReportTemplate::getCreateTime);
        return this.list(wrapper);
    }

    @Override
    public ReportTemplate getById(Long id) {
        return super.getById(id);
    }

    @Override
    public ReportTemplate getByInstrumentId(Long instrumentId) {
        if (instrumentId == null) {
            return getDefault();
        }
        // 优先查仪器专属模板
        LambdaQueryWrapper<ReportTemplate> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(ReportTemplate::getInstrumentId, instrumentId)
               .eq(ReportTemplate::getStatus, 1)
               .last("LIMIT 1");
        ReportTemplate template = this.getOne(wrapper);
        if (template == null) {
            template = getDefault();
        }
        return template;
    }

    @Override
    public ReportTemplate getByInstrumentAndType(Long instrumentId, Integer reportType) {
        // 1. 优先查找仪器+类型专属模板
        if (instrumentId != null && reportType != null) {
            LambdaQueryWrapper<ReportTemplate> wrapper = new LambdaQueryWrapper<>();
            wrapper.eq(ReportTemplate::getInstrumentId, instrumentId)
                   .eq(ReportTemplate::getReportType, reportType)
                   .eq(ReportTemplate::getStatus, 1)
                   .last("LIMIT 1");
            ReportTemplate template = this.getOne(wrapper);
            if (template != null) {
                return template;
            }
        }
        
        // 2. 查找类型专属通用模板（不绑定仪器）
        if (reportType != null) {
            LambdaQueryWrapper<ReportTemplate> wrapper = new LambdaQueryWrapper<>();
            wrapper.isNull(ReportTemplate::getInstrumentId)
                   .eq(ReportTemplate::getReportType, reportType)
                   .eq(ReportTemplate::getStatus, 1)
                   .last("LIMIT 1");
            ReportTemplate template = this.getOne(wrapper);
            if (template != null) {
                return template;
            }
        }
        
        // 3. 回退：按仪器查找
        if (instrumentId != null) {
            LambdaQueryWrapper<ReportTemplate> wrapper = new LambdaQueryWrapper<>();
            wrapper.eq(ReportTemplate::getInstrumentId, instrumentId)
                   .eq(ReportTemplate::getStatus, 1)
                   .last("LIMIT 1");
            ReportTemplate template = this.getOne(wrapper);
            if (template != null) {
                return template;
            }
        }
        
        // 4. 最终回退：默认模板
        return getDefault();
    }

    @Override
    public ReportTemplate getDefaultByType(Integer reportType) {
        if (reportType != null) {
            // 优先查找该类型的默认模板
            LambdaQueryWrapper<ReportTemplate> wrapper = new LambdaQueryWrapper<>();
            wrapper.eq(ReportTemplate::getReportType, reportType)
                   .eq(ReportTemplate::getIsDefault, 1)
                   .eq(ReportTemplate::getStatus, 1)
                   .last("LIMIT 1");
            ReportTemplate template = this.getOne(wrapper);
            if (template != null) {
                return template;
            }
            
            // 查找该类型的第一个模板
            wrapper = new LambdaQueryWrapper<>();
            wrapper.eq(ReportTemplate::getReportType, reportType)
                   .eq(ReportTemplate::getStatus, 1)
                   .orderByDesc(ReportTemplate::getCreateTime)
                   .last("LIMIT 1");
            template = this.getOne(wrapper);
            if (template != null) {
                return template;
            }
        }
        return getDefault();
    }

    @Override
    public ReportTemplate getDefault() {
        LambdaQueryWrapper<ReportTemplate> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(ReportTemplate::getIsDefault, 1)
               .eq(ReportTemplate::getStatus, 1)
               .last("LIMIT 1");
        ReportTemplate template = this.getOne(wrapper);
        if (template == null) {
            // 没有默认模板,取第一个可用的
            wrapper = new LambdaQueryWrapper<>();
            wrapper.eq(ReportTemplate::getStatus, 1)
                   .orderByDesc(ReportTemplate::getCreateTime)
                   .last("LIMIT 1");
            template = this.getOne(wrapper);
        }
        return template;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ReportTemplate create(ReportTemplate template) {
        template.setCreateTime(LocalDateTime.now());
        template.setUpdateTime(LocalDateTime.now());
        if (template.getStatus() == null) {
            template.setStatus(1);
        }
        if (template.getDualColumn() == null) {
            template.setDualColumn(0);
        }
        if (template.getIsDefault() != null && template.getIsDefault() == 1) {
            clearOtherDefaults(null);
        }
        this.save(template);
        return template;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ReportTemplate update(ReportTemplate template) {
        template.setUpdateTime(LocalDateTime.now());
        if (template.getIsDefault() != null && template.getIsDefault() == 1) {
            clearOtherDefaults(template.getId());
        }
        this.updateById(template);
        return template;
    }

    @Override
    public void delete(Long id) {
        this.removeById(id);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void setDefault(Long id) {
        clearOtherDefaults(id);
        ReportTemplate template = new ReportTemplate();
        template.setId(id);
        template.setIsDefault(1);
        template.setUpdateTime(LocalDateTime.now());
        this.updateById(template);
    }

    private void clearOtherDefaults(Long excludeId) {
        LambdaQueryWrapper<ReportTemplate> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(ReportTemplate::getIsDefault, 1);
        if (excludeId != null) {
            wrapper.ne(ReportTemplate::getId, excludeId);
        }
        List<ReportTemplate> defaults = this.list(wrapper);
        for (ReportTemplate t : defaults) {
            t.setIsDefault(0);
            t.setUpdateTime(LocalDateTime.now());
            this.updateById(t);
        }
    }
}
