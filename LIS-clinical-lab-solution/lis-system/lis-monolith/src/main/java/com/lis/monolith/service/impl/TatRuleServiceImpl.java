package com.lis.monolith.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.lis.common.core.exception.BusinessException;
import com.lis.monolith.entity.TatRule;
import com.lis.monolith.mapper.TatRuleMapper;
import com.lis.monolith.service.TatRuleService;
import com.lis.system.entity.SysConfig;
import com.lis.system.mapper.SysConfigMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.List;

/**
 * TAT 规则服务实现
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TatRuleServiceImpl implements TatRuleService {

    /** 系统参数: 是否启用 TAT 规则 */
    public static final String CFG_TAT_ENABLED = "tat.rules.enabled";

    private final TatRuleMapper tatRuleMapper;
    private final SysConfigMapper sysConfigMapper;

    @Override
    public IPage<TatRule> page(int page, int size, String specimenType, String keyword) {
        LambdaQueryWrapper<TatRule> qw = new LambdaQueryWrapper<>();
        if (StringUtils.hasText(specimenType)) {
            qw.eq(TatRule::getSpecimenType, specimenType);
        }
        if (StringUtils.hasText(keyword)) {
            qw.and(w -> w.like(TatRule::getTestItemName, keyword)
                    .or().like(TatRule::getSpecimenType, keyword));
        }
        qw.orderByDesc(TatRule::getId);
        return tatRuleMapper.selectPage(new Page<>(Math.max(page, 1), Math.max(size, 1)), qw);
    }

    @Override
    public List<TatRule> listAll() {
        return tatRuleMapper.selectList(new LambdaQueryWrapper<TatRule>().orderByDesc(TatRule::getId));
    }

    @Override
    public TatRule create(TatRule rule) {
        if (rule.getTestItemId() == null) {
            throw new BusinessException("检验项目不能为空");
        }
        if (rule.getUrgentTimeout() == null || rule.getNormalTimeout() == null) {
            throw new BusinessException("超时时限不能为空");
        }
        // 标本类型空/null 规范化为 "", 表示匹配全部标本类型 (数据库 NOT NULL)
        if (!StringUtils.hasText(rule.getSpecimenType())) {
            rule.setSpecimenType("");
        }
        // 查重: 同一 标本类型+检验项目 只允许一条 (specimenType="" 表示全部, 同样只允许一条)
        LambdaQueryWrapper<TatRule> dup = new LambdaQueryWrapper<>();
        dup.eq(TatRule::getTestItemId, rule.getTestItemId());
        dup.eq(TatRule::getSpecimenType, rule.getSpecimenType());
        Long exists = tatRuleMapper.selectCount(dup);
        if (exists != null && exists > 0) {
            throw new BusinessException(rule.getSpecimenType().isEmpty()
                    ? "该检验项目已存在【全部标本类型】的TAT规则, 无需重复创建"
                    : "该标本类型+检验项目已存在TAT规则");
        }
        rule.setId(null);
        rule.setEnabled(rule.getEnabled() == null ? 1 : rule.getEnabled());
        rule.setCreateTime(LocalDateTime.now());
        rule.setUpdateTime(LocalDateTime.now());
        tatRuleMapper.insert(rule);
        return rule;
    }

    @Override
    public TatRule update(TatRule rule) {
        TatRule db = tatRuleMapper.selectById(rule.getId());
        if (db == null) {
            throw new BusinessException("TAT规则不存在");
        }
        // 标本类型空/null 规范化为 "", 表示匹配全部标本类型 (数据库 NOT NULL)
        if (!StringUtils.hasText(rule.getSpecimenType())) {
            rule.setSpecimenType("");
        }
        db.setSpecimenType(rule.getSpecimenType());
        db.setTestItemId(rule.getTestItemId());
        db.setTestItemName(rule.getTestItemName());
        db.setUrgentTimeout(rule.getUrgentTimeout());
        db.setNormalTimeout(rule.getNormalTimeout());
        db.setEnabled(rule.getEnabled());
        db.setRemark(rule.getRemark());
        db.setUpdateTime(LocalDateTime.now());
        tatRuleMapper.updateById(db);
        return db;
    }

    @Override
    public void delete(Long id) {
        tatRuleMapper.deleteById(id);
    }

    @Override
    public boolean isEnabled() {
        SysConfig cfg = sysConfigMapper.selectOne(new LambdaQueryWrapper<SysConfig>()
                .eq(SysConfig::getConfigKey, CFG_TAT_ENABLED));
        return cfg != null && "1".equals(cfg.getConfigValue());
    }

    @Override
    public void setEnabled(boolean enabled) {
        SysConfig cfg = sysConfigMapper.selectOne(new LambdaQueryWrapper<SysConfig>()
                .eq(SysConfig::getConfigKey, CFG_TAT_ENABLED));
        if (cfg == null) {
            cfg = new SysConfig();
            cfg.setConfigKey(CFG_TAT_ENABLED);
            cfg.setConfigName("TAT超时通知");
            cfg.setConfigValue(enabled ? "1" : "0");
            cfg.setRemark("是否启用TAT超时规则监控");
            cfg.setCreateTime(LocalDateTime.now());
            cfg.setUpdateTime(LocalDateTime.now());
            sysConfigMapper.insert(cfg);
        } else {
            cfg.setConfigValue(enabled ? "1" : "0");
            cfg.setUpdateTime(LocalDateTime.now());
            sysConfigMapper.updateById(cfg);
        }
        log.info("TAT规则启用状态已更新: enabled={}", enabled);
    }
}