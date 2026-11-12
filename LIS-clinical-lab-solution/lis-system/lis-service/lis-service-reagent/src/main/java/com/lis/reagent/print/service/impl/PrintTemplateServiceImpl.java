package com.lis.reagent.print.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lis.common.core.exception.BusinessException;
import com.lis.reagent.mapper.PrintTemplateMapper;
import com.lis.reagent.print.entity.PrintTemplate;
import com.lis.reagent.print.service.PrintFieldCatalog;
import com.lis.reagent.print.service.PrintTemplateService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 打印模板服务实现。
 *
 * <p>默认模板唯一性由「业务层先清零 + 数据库部分唯一索引兜底」共同保证。</p>
 *
 * @author LIS Team
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PrintTemplateServiceImpl extends ServiceImpl<PrintTemplateMapper, PrintTemplate>
        implements PrintTemplateService {

    private static final ObjectMapper JSON = new ObjectMapper();

    private static final int ENABLED = 1;
    private static final int DEFAULT_FLAG = 1;

    @Override
    public Page<PrintTemplate> queryPage(long current, long size, String bizType, String keyword, Integer status) {
        LambdaQueryWrapper<PrintTemplate> wrapper = new LambdaQueryWrapper<>();
        if (bizType != null && !bizType.isBlank()) {
            wrapper.eq(PrintTemplate::getBizType, bizType);
        }
        if (status != null) {
            wrapper.eq(PrintTemplate::getStatus, status);
        }
        if (keyword != null && !keyword.isBlank()) {
            wrapper.and(w -> w.like(PrintTemplate::getTemplateName, keyword)
                    .or().like(PrintTemplate::getTemplateCode, keyword));
        }
        wrapper.orderByDesc(PrintTemplate::getIsDefault).orderByDesc(PrintTemplate::getUpdateTime);
        return page(new Page<>(current, size), wrapper);
    }

    @Override
    public List<PrintTemplate> listEnabled(String bizType) {
        LambdaQueryWrapper<PrintTemplate> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(PrintTemplate::getStatus, ENABLED);
        if (bizType != null && !bizType.isBlank()) {
            wrapper.eq(PrintTemplate::getBizType, bizType);
        }
        wrapper.orderByDesc(PrintTemplate::getIsDefault).orderByDesc(PrintTemplate::getUpdateTime);
        return list(wrapper);
    }

    @Override
    public PrintTemplate getDetail(Long id) {
        PrintTemplate template = getById(id);
        if (template == null) {
            throw new BusinessException("打印模板不存在");
        }
        return template;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public PrintTemplate saveTemplate(PrintTemplate template) {
        if (template == null) {
            throw new BusinessException("模板内容不能为空");
        }
        if (template.getBizType() == null || !PrintFieldCatalog.supports(template.getBizType())) {
            throw new BusinessException("不支持的业务类型");
        }
        if (template.getTemplateCode() == null || template.getTemplateCode().isBlank()) {
            throw new BusinessException("模板编码不能为空");
        }
        if (template.getTemplateName() == null || template.getTemplateName().isBlank()) {
            throw new BusinessException("模板名称不能为空");
        }
        checkCodeUnique(template.getTemplateCode().trim(), template.getId());
        validateTemplateJson(template.getTemplateJson());

        if (template.getPaperWidth() == null || template.getPaperWidth().compareTo(BigDecimal.ZERO) <= 0
                || template.getPaperHeight() == null || template.getPaperHeight().compareTo(BigDecimal.ZERO) <= 0) {
            throw new BusinessException("纸张宽高必须大于 0");
        }
        if (template.getMargin() == null) {
            template.setMargin(BigDecimal.ZERO);
        }
        if (template.getStatus() == null) {
            template.setStatus(ENABLED);
        }
        if (template.getIsDefault() == null) {
            template.setIsDefault(0);
        }
        template.setTemplateCode(template.getTemplateCode().trim());
        template.setTemplateName(template.getTemplateName().trim());
        template.setUpdateTime(LocalDateTime.now());

        if (template.getIsDefault() == DEFAULT_FLAG) {
            clearOtherDefault(template.getBizType(), template.getId());
        }

        if (template.getId() == null) {
            save(template);
        } else {
            updateById(template);
        }
        return template;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        PrintTemplate template = getDetail(id);
        if (Integer.valueOf(DEFAULT_FLAG).equals(template.getIsDefault())) {
            throw new BusinessException("默认模板不可删除，请先将其他模板设为默认");
        }
        removeById(id);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void setDefault(Long id) {
        PrintTemplate template = getDetail(id);
        if (!Integer.valueOf(ENABLED).equals(template.getStatus())) {
            throw new BusinessException("停用的模板不能设为默认，请先启用");
        }
        clearOtherDefault(template.getBizType(), id);
        LambdaUpdateWrapper<PrintTemplate> update = new LambdaUpdateWrapper<>();
        update.eq(PrintTemplate::getId, id)
                .set(PrintTemplate::getIsDefault, DEFAULT_FLAG)
                .set(PrintTemplate::getUpdateTime, LocalDateTime.now());
        update(update);
    }

    @Override
    public PrintTemplate getDefaultOrFirst(String bizType) {
        List<PrintTemplate> list = listEnabled(bizType);
        if (list.isEmpty()) {
            return null;
        }
        return list.stream()
                .filter(t -> Integer.valueOf(DEFAULT_FLAG).equals(t.getIsDefault()))
                .findFirst()
                .orElse(list.get(0));
    }

    private void clearOtherDefault(String bizType, Long excludeId) {
        LambdaUpdateWrapper<PrintTemplate> update = new LambdaUpdateWrapper<>();
        update.eq(PrintTemplate::getBizType, bizType)
                .eq(PrintTemplate::getIsDefault, DEFAULT_FLAG)
                .set(PrintTemplate::getIsDefault, 0);
        if (excludeId != null) {
            update.ne(PrintTemplate::getId, excludeId);
        }
        update(update);
    }

    private void checkCodeUnique(String templateCode, Long excludeId) {
        LambdaQueryWrapper<PrintTemplate> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(PrintTemplate::getTemplateCode, templateCode);
        if (excludeId != null) {
            wrapper.ne(PrintTemplate::getId, excludeId);
        }
        if (count(wrapper) > 0) {
            throw new BusinessException("模板编码已存在：" + templateCode);
        }
    }

    /** 模板 JSON 必须是合法对象且包含非空的 elements 数组 */
    private void validateTemplateJson(String templateJson) {
        if (templateJson == null || templateJson.isBlank()) {
            throw new BusinessException("模板内容不能为空");
        }
        try {
            JsonNode root = JSON.readTree(templateJson);
            JsonNode elements = root.get("elements");
            if (elements == null || !elements.isArray()) {
                throw new BusinessException("模板内容缺少 elements 数组");
            }
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            throw new BusinessException("模板内容不是合法的 JSON");
        }
    }
}
