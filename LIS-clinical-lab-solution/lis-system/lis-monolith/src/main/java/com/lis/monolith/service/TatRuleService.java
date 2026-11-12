package com.lis.monolith.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.lis.monolith.entity.TatRule;

import java.util.List;

/**
 * TAT 规则服务
 */
public interface TatRuleService {

    IPage<TatRule> page(int page, int size, String specimenType, String keyword);

    List<TatRule> listAll();

    TatRule create(TatRule rule);

    TatRule update(TatRule rule);

    void delete(Long id);

    /** 是否启用 TAT 规则 (sys_config: tat.rules.enabled) */
    boolean isEnabled();

    /** 设置是否启用 TAT 规则 */
    void setEnabled(boolean enabled);
}