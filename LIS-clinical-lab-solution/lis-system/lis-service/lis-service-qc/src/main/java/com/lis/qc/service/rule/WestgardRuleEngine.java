package com.lis.qc.service.rule;

import com.lis.qc.entity.QCLot;
import com.lis.qc.entity.QCRecord;
import com.lis.qc.mapper.QcRuleMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Westgard 多规则引擎
 * <p>
 * 持有所有 {@link WestgardRule} 实现 (由 Spring 自动注入)。
 * 规则分为两类:
 * <ul>
 *   <li>REJECT (1-3s, 2-2s, R-4s): 任一命中即失控</li>
 *   <li>WARNING (1-2s, 4-1s, 10x): 命中返回警告但不拒绝</li>
 * </ul>
 * 评估顺序: 先检查 REJECT 规则，任一命中则返回 REJECT；
 * 再检查 WARNING 规则，命中则返回 WARNING (不拒绝)；否则 ACCEPT。
 * 仅执行数据库中启用的规则 (lis_qc_rule.enabled=1)。
 */
@Slf4j
@Component
public class WestgardRuleEngine {

    private final List<WestgardRule> rules;
    private final QcRuleMapper qcRuleMapper;

    @Autowired
    public WestgardRuleEngine(List<WestgardRule> rules, QcRuleMapper qcRuleMapper) {
        this.rules = rules;
        this.qcRuleMapper = qcRuleMapper;
        log.info("Westgard 规则引擎加载规则 {} 条", rules.size());
        rules.forEach(r -> log.info("  规则: {} [{}]", r.getRuleName(), r.getRuleType()));
    }

    /**
     * 评估质控
     * @param recentRecords 最近记录 (按时间升序，最后一条为最新)
     * @param lot 当前质控品批号
     */
    public QCResult evaluate(List<QCRecord> recentRecords, QCLot lot) {
        if (recentRecords == null || recentRecords.isEmpty() || lot == null) {
            return QCResult.reject("PreCheck", "数据不完整，无法判定");
        }
        List<WestgardRule> activeRules = getActiveRules();

        // 1. 先检查 REJECT 规则，任一命中则返回 REJECT
        for (WestgardRule rule : activeRules) {
            if (!WestgardRule.TYPE_REJECT.equals(rule.getRuleType())) {
                continue;
            }
            try {
                if (rule.matches(recentRecords, lot)) {
                    log.warn("Westgard 规则 [{}] 触发失控: {}", rule.getRuleName(), rule.getSuggestion());
                    return QCResult.reject(rule.getRuleName(), rule.getSuggestion());
                }
            } catch (Exception e) {
                log.error("规则 {} 执行异常", rule.getRuleName(), e);
            }
        }

        // 2. 再检查 WARNING 规则，命中返回 WARNING 但不拒绝
        for (WestgardRule rule : activeRules) {
            if (!WestgardRule.TYPE_WARNING.equals(rule.getRuleType())) {
                continue;
            }
            try {
                if (rule.matches(recentRecords, lot)) {
                    log.warn("Westgard 规则 [{}] 触发警告: {}", rule.getRuleName(), rule.getSuggestion());
                    return QCResult.warning(rule.getRuleName(), rule.getSuggestion());
                }
            } catch (Exception e) {
                log.error("规则 {} 执行异常", rule.getRuleName(), e);
            }
        }
        return QCResult.accept();
    }

    /**
     * 评估质控 (使用实际均值和SD)
     * @param recentRecords 最近记录 (按时间升序，最后一条为最新)
     * @param actualMean 实际均值 (由测量数据计算得出)
     * @param actualSd   实际标准差 (由测量数据计算得出)
     */
    public QCResult evaluate(List<QCRecord> recentRecords, BigDecimal actualMean, BigDecimal actualSd) {
        if (recentRecords == null || recentRecords.isEmpty() || actualMean == null || actualSd == null) {
            return QCResult.reject("PreCheck", "数据不完整，无法判定");
        }
        List<WestgardRule> activeRules = getActiveRules();

        // 1. 先检查 REJECT 规则，任一命中则返回 REJECT
        for (WestgardRule rule : activeRules) {
            if (!WestgardRule.TYPE_REJECT.equals(rule.getRuleType())) {
                continue;
            }
            try {
                if (rule.matches(recentRecords, actualMean, actualSd)) {
                    log.warn("Westgard 规则 [{}] 触发失控 (实际均值/SD): {}", rule.getRuleName(), rule.getSuggestion());
                    return QCResult.reject(rule.getRuleName(), rule.getSuggestion());
                }
            } catch (Exception e) {
                log.error("规则 {} 执行异常", rule.getRuleName(), e);
            }
        }

        // 2. 再检查 WARNING 规则，命中返回 WARNING 但不拒绝
        for (WestgardRule rule : activeRules) {
            if (!WestgardRule.TYPE_WARNING.equals(rule.getRuleType())) {
                continue;
            }
            try {
                if (rule.matches(recentRecords, actualMean, actualSd)) {
                    log.warn("Westgard 规则 [{}] 触发警告 (实际均值/SD): {}", rule.getRuleName(), rule.getSuggestion());
                    return QCResult.warning(rule.getRuleName(), rule.getSuggestion());
                }
            } catch (Exception e) {
                log.error("规则 {} 执行异常", rule.getRuleName(), e);
            }
        }
        return QCResult.accept();
    }

    /**
     * 获取启用的规则。
     * 从数据库 lis_qc_rule 读取 enabled=1 的 rule_code，过滤规则链；
     * 若数据库无配置则回退到全部规则 (保证功能可用)。
     */
    private List<WestgardRule> getActiveRules() {
        Set<String> enabledCodes;
        try {
            enabledCodes = qcRuleMapper.selectList(
                            new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<com.lis.qc.entity.QcRule>()
                                    .eq(com.lis.qc.entity.QcRule::getEnabled, 1))
                    .stream()
                    .map(com.lis.qc.entity.QcRule::getRuleCode)
                    .collect(Collectors.toSet());
        } catch (Exception e) {
            log.warn("读取质控规则配置失败, 使用全部规则: {}", e.getMessage());
            return rules;
        }
        if (enabledCodes.isEmpty()) {
            return rules;
        }
        return rules.stream()
                // 按数据库 lis_qc_rule.rule_code 过滤 (与各规则实现 getRuleName() 返回的编码如 "1-3s" 一一对应)
                .filter(r -> enabledCodes.contains(r.getRuleName()))
                .collect(Collectors.toList());
    }
}
