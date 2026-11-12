package com.lis.qc.service.rule;

import com.lis.qc.entity.QCLot;
import com.lis.qc.entity.QCRecord;

import java.math.BigDecimal;
import java.util.List;

/**
 * Westgard 多规则接口 (策略模式)
 * <p>
 * 每条规则由 {@link WestgardRuleEngine} 自动收集，按规则链顺序匹配。
 */
public interface WestgardRule {

    /** 规则类型: 警告 (不拒绝) */
    String TYPE_WARNING = "WARNING";
    /** 规则类型: 拒绝 (失控) */
    String TYPE_REJECT = "REJECT";

    /**
     * 规则名称 (如 "1-3s", "2-2s")
     */
    String getRuleName();

    /**
     * 规则类型: {@link #TYPE_WARNING} 警告 (不拒绝) / {@link #TYPE_REJECT} 拒绝 (失控)
     * 默认为 REJECT
     */
    default String getRuleType() {
        return TYPE_REJECT;
    }

    /**
     * 判断规则是否匹配 (匹配表示失控)
     * @param recentRecords 最近若干条质控记录 (按时间升序，最后一条为最新)
     * @param lot 当前质控品批号 (含靶值/SD)
     */
    boolean matches(List<QCRecord> recentRecords, QCLot lot);

    /**
     * 判断规则是否匹配 (使用实际均值和SD)
     * @param recentRecords 最近若干条质控记录 (按时间升序，最后一条为最新)
     * @param actualMean 实际均值 (由测量数据计算得出)
     * @param actualSd   实际标准差 (由测量数据计算得出)
     */
    boolean matches(List<QCRecord> recentRecords, BigDecimal actualMean, BigDecimal actualSd);

    /**
     * 失控时的处理建议
     */
    String getSuggestion();
}
