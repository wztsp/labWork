package com.lis.micro.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.IService;
import com.lis.micro.entity.ResistanceRule;

import java.util.List;

/**
 * 特殊耐药规则服务
 */
public interface ResistanceRuleService extends IService<ResistanceRule> {

    Page<ResistanceRule> queryPage(long current, long size, String keyword, String ruleType);

    List<ResistanceRule> listAllEnabled();

    /**
     * 根据菌株编码查询耐药规则
     */
    List<ResistanceRule> matchByOrganism(String organismCode);

    ResistanceRule create(ResistanceRule rule);

    ResistanceRule update(ResistanceRule rule);

    void delete(Long id);
}
