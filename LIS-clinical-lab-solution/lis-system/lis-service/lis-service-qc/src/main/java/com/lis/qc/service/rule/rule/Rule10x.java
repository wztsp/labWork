package com.lis.qc.service.rule.rule;

import com.lis.qc.entity.QCLot;
import com.lis.qc.entity.QCRecord;
import com.lis.qc.service.rule.WestgardRule;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.List;

/**
 * Westgard 10x 拒绝规则
 * <p>
 * 连续 10 个质控点都在均值同侧。
 */
@Component
public class Rule10x implements WestgardRule {

    @Override
    public String getRuleName() {
        return "10x";
    }

    @Override
    public String getRuleType() {
        return TYPE_WARNING;
    }

    @Override
    public boolean matches(List<QCRecord> recentRecords, QCLot lot) {
        if (recentRecords.size() < 10) return false;
        int n = recentRecords.size();
        boolean allPositive = true;
        boolean allNegative = true;
        for (int i = n - 10; i < n; i++) {
            BigDecimal z = Rule12s.calcZScore(recentRecords.get(i), lot);
            if (z == null) return false;
            if (z.compareTo(BigDecimal.ZERO) <= 0) allPositive = false;
            if (z.compareTo(BigDecimal.ZERO) >= 0) allNegative = false;
        }
        return allPositive || allNegative;
    }

    @Override
    public boolean matches(List<QCRecord> recentRecords, BigDecimal actualMean, BigDecimal actualSd) {
        if (recentRecords.size() < 10) return false;
        int n = recentRecords.size();
        boolean allPositive = true;
        boolean allNegative = true;
        for (int i = n - 10; i < n; i++) {
            BigDecimal z = Rule12s.calcZScore(recentRecords.get(i).getMeasureValue(), actualMean, actualSd);
            if (z == null) return false;
            if (z.compareTo(BigDecimal.ZERO) <= 0) allPositive = false;
            if (z.compareTo(BigDecimal.ZERO) >= 0) allNegative = false;
        }
        return allPositive || allNegative;
    }

    @Override
    public String getSuggestion() {
        return "10x 失控: 连续10个点在均值同侧，提示系统偏差，请重新校准/检查试剂批号";
    }
}
