package com.lis.qc.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.lis.common.core.exception.BusinessException;
import com.lis.qc.entity.QcRule;
import com.lis.qc.mapper.QcRuleMapper;
import com.lis.qc.service.QcRuleService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Westgard 规则配置服务实现
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class QcRuleServiceImpl implements QcRuleService {

    private final QcRuleMapper qcRuleMapper;

    @Override
    public List<QcRule> listAll() {
        return qcRuleMapper.selectList(new LambdaQueryWrapper<QcRule>()
                .orderByAsc(QcRule::getId));
    }

    @Override
    public List<QcRule> listEnabled() {
        return qcRuleMapper.selectList(new LambdaQueryWrapper<QcRule>()
                .eq(QcRule::getEnabled, 1)
                .orderByAsc(QcRule::getId));
    }

    @Override
    public QcRule getById(Long id) {
        return qcRuleMapper.selectById(id);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long create(QcRule rule) {
        if (rule.getRuleCode() == null || rule.getRuleCode().isEmpty()) {
            throw new BusinessException("规则编码不能为空");
        }
        if (rule.getRuleType() == null) {
            rule.setRuleType("REJECT");
        }
        if (rule.getEnabled() == null) {
            rule.setEnabled(1);
        }
        rule.setCreateTime(LocalDateTime.now());
        qcRuleMapper.insert(rule);
        log.info("新增质控规则: code={}, type={}", rule.getRuleCode(), rule.getRuleType());
        return rule.getId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean update(QcRule rule) {
        if (rule.getId() == null) {
            throw new BusinessException("规则ID不能为空");
        }
        qcRuleMapper.updateById(rule);
        return true;
    }

    @Override
    public boolean delete(Long id) {
        return qcRuleMapper.deleteById(id) > 0;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean toggleEnabled(Long id, int enabled) {
        QcRule rule = qcRuleMapper.selectById(id);
        if (rule == null) {
            throw new BusinessException("规则不存在: " + id);
        }
        rule.setEnabled(enabled);
        qcRuleMapper.updateById(rule);
        log.info("切换规则启用状态: code={}, enabled={}", rule.getRuleCode(), enabled);
        return true;
    }
}
