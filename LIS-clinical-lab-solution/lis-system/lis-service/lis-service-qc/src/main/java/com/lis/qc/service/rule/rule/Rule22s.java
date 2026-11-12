package com.lis.qc.service.rule.rule;

import com.lis.qc.entity.QCLot;
import com.lis.qc.entity.QCRecord;
import com.lis.qc.service.rule.WestgardRule;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.List;

/**
 * Westgard 2-2s 拒绝规则
 * <p>
 * 连续 2 个质控点超出同侧均值 ± 2SD。
 */
@Component
public class Rule22s implements WestgardRule {

    @Override
    public String getRuleName() {
        return "2-2s";
    }

    @Override
    public boolean matches(List<QCRecord> recentRecords, QCLot lot) {
        if (recentRecords.size() < 2) return false;
        int n = recentRecords.size();
        QCRecord last1 = recentRecords.get(n - 1);
        QCRecord last2 = recentRecords.get(n - 2);
        BigDecimal z1 = Rule12s.calcZScore(last1, lot);
        BigDecimal z2 = Rule12s.calcZScore(last2, lot);
        if (z1 == null || z2 == null) return false;
        // 同侧且均 |z| >= 2 (Westgard 标准定义: 超出±2SD 及其以上)
        boolean bothHigh = z1.compareTo(BigDecimal.valueOf(2)) >= 0
                && z2.compareTo(BigDecimal.valueOf(2)) >= 0;
        boolean bothLow = z1.compareTo(BigDecimal.valueOf(-2)) <= 0
                && z2.compareTo(BigDecimal.valueOf(-2)) <= 0;
        return bothHigh || bothLow;
    }

    @Override
    public boolean matches(List<QCRecord> recentRecords, BigDecimal actualMean, BigDecimal actualSd) {
        if (recentRecords.size() < 2) return false;
        int n = recentRecords.size();
        QCRecord last1 = recentRecords.get(n - 1);
        QCRecord last2 = recentRecords.get(n - 2);
        BigDecimal z1 = Rule12s.calcZScore(last1.getMeasureValue(), actualMean, actualSd);
        BigDecimal z2 = Rule12s.calcZScore(last2.getMeasureValue(), actualMean, actualSd);
        if (z1 == null || z2 == null) return false;
        boolean bothHigh = z1.compareTo(BigDecimal.valueOf(2)) >= 0
                && z2.compareTo(BigDecimal.valueOf(2)) >= 0;
        boolean bothLow = z1.compareTo(BigDecimal.valueOf(-2)) <= 0
                && z2.compareTo(BigDecimal.valueOf(-2)) <= 0;
        return bothHigh || bothLow;
    }

    @Override
    public String getSuggestion() {
        return "2-2s 失控: 连续2个点同侧超出均值±2SD，提示存在系统误差，请检查试剂批号/校准";
    }
}
