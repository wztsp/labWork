package com.lis.micro.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.lis.common.core.exception.BusinessException;
import com.lis.micro.entity.Antibiotic;
import com.lis.micro.entity.Bacteria;
import com.lis.micro.entity.ResistanceRule;
import com.lis.micro.mapper.ResistanceRuleMapper;
import com.lis.micro.service.AntibioticService;
import com.lis.micro.service.BacteriaService;
import com.lis.micro.service.ResistanceRuleService;
import com.lis.micro.util.CodeListUtil;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 特殊耐药规则服务实现
 */
@Service
public class ResistanceRuleServiceImpl extends ServiceImpl<ResistanceRuleMapper, ResistanceRule>
        implements ResistanceRuleService {

    private final BacteriaService bacteriaService;
    private final AntibioticService antibioticService;

    public ResistanceRuleServiceImpl(BacteriaService bacteriaService, AntibioticService antibioticService) {
        this.bacteriaService = bacteriaService;
        this.antibioticService = antibioticService;
    }

    @Override
    public Page<ResistanceRule> queryPage(long current, long size, String keyword, String ruleType) {
        LambdaQueryWrapper<ResistanceRule> wrapper = new LambdaQueryWrapper<>();
        if (keyword != null && !keyword.isEmpty()) {
            wrapper.and(w -> w.like(ResistanceRule::getRuleName, keyword)
                    .or().like(ResistanceRule::getOrganismCode, keyword)
                    .or().like(ResistanceRule::getBacteriaCodes, keyword)
                    .or().like(ResistanceRule::getAntibioticCodes, keyword));
        }
        if (ruleType != null && !ruleType.isEmpty()) {
            wrapper.eq(ResistanceRule::getRuleType, ruleType);
        }
        wrapper.orderByAsc(ResistanceRule::getIsBuiltin).orderByDesc(ResistanceRule::getCreateTime);
        Page<ResistanceRule> page = this.page(new Page<>(current, size), wrapper);
        enrichNames(page.getRecords());
        return page;
    }

    @Override
    public List<ResistanceRule> listAllEnabled() {
        List<ResistanceRule> rules = this.list(new LambdaQueryWrapper<ResistanceRule>().eq(ResistanceRule::getStatus, 1));
        enrichNames(rules);
        return rules;
    }

    @Override
    public List<ResistanceRule> matchByOrganism(String organismCode) {
        List<ResistanceRule> rules = this.list(new LambdaQueryWrapper<ResistanceRule>()
                .eq(ResistanceRule::getOrganismCode, organismCode)
                .eq(ResistanceRule::getStatus, 1));
        enrichNames(rules);
        return rules;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ResistanceRule create(ResistanceRule rule) {
        if (rule.getRuleName() == null || rule.getRuleName().isEmpty()) {
            throw new BusinessException("规则名称不能为空");
        }
        if (rule.getStatus() == null) {
            rule.setStatus(1);
        }
        if (rule.getIsCritical() == null) {
            rule.setIsCritical(0);
        }
        if (rule.getMatchMode() == null || rule.getMatchMode().isEmpty()) {
            rule.setMatchMode("ANY_RESISTANT");
        }
        if (rule.getIsBuiltin() == null) {
            rule.setIsBuiltin(0);
        }
        rule.setCreateTime(LocalDateTime.now());
        this.save(rule);
        return rule;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ResistanceRule update(ResistanceRule rule) {
        if (rule.getId() == null) {
            throw new BusinessException("ID不能为空");
        }
        this.updateById(rule);
        return rule;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        this.removeById(id);
    }

    /** 为规则补全细菌/抗生素名称(用于展示) */
    private void enrichNames(List<ResistanceRule> rules) {
        if (rules == null || rules.isEmpty()) {
            return;
        }
        List<Bacteria> bList = bacteriaService.listAllEnabled();
        List<Antibiotic> aList = antibioticService.listAllEnabled();
        Map<String, String> bName = bList.stream()
                .filter(b -> b.getBacteriaCode() != null)
                .collect(Collectors.toMap(Bacteria::getBacteriaCode, Bacteria::getBacteriaName, (x1, x2) -> x1));
        Map<String, String> aName = aList.stream()
                .filter(a -> a.getAntibioticCode() != null)
                .collect(Collectors.toMap(Antibiotic::getAntibioticCode, Antibiotic::getAntibioticName, (x1, x2) -> x1));

        for (ResistanceRule rule : rules) {
            // 旧数据: organismCode 单个 -> 并入 bacteriaCodes 名称
            java.util.List<String> bCodes = new java.util.ArrayList<>(CodeListUtil.parseCodes(rule.getBacteriaCodes()));
            if (rule.getOrganismCode() != null && !rule.getOrganismCode().isEmpty()) {
                bCodes.add(rule.getOrganismCode());
            }
            rule.setBacteriaNames(joinNames(bCodes, bName));
            rule.setAntibioticNames(joinNames(CodeListUtil.parseCodes(rule.getAntibioticCodes()), aName));
        }
    }

    private String joinNames(java.util.List<String> codes, Map<String, String> nameMap) {
        if (codes.isEmpty()) {
            return "（所有细菌）";
        }
        return codes.stream().map(c -> nameMap.getOrDefault(c, c)).collect(Collectors.joining("、"));
    }
}