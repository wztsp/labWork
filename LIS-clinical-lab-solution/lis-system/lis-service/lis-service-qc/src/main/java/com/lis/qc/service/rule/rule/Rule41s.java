package com.lis.qc.service.rule.rule;

import com.lis.qc.entity.QCLot;
import com.lis.qc.entity.QCRecord;
import com.lis.qc.service.rule.WestgardRule;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.List;

/**
 * Westgard 4-1s 拒绝规则
 * <p>
 * 连续 4 个质控点超出同侧均值 ± 1SD。
 */
@Component
public class Rule41s implements WestgardRule {

    @Override
    public String getRuleName() {
        return "4-1s";
    }

    @Override
    public String getRuleType() {
        return TYPE_WARNING;
    }

    @Override
    public boolean matches(List<QCRecord> recentRecords, QCLot lot) {
        if (recentRecords.size() < 4) return false;
        int n = recentRecords.size();
        boolean allHigh = true;
        boolean allLow = true;
        for (int i = n - 4; i < n; i++) {
            BigDecimal z = Rule12s.calcZScore(recentRecords.get(i), lot);
            if (z == null) return false;
            // 同侧且均 |z| >= 1 (Westgard 标准定义: 连续4点同侧超出±1SD 及以上)
            if (z.compareTo(BigDecimal.valueOf(1)) < 0) allHigh = false;
            if (z.compareTo(BigDecimal.valueOf(-1)) > 0) allLow = false;
        }
        return allHigh || allLow;
    }

    @Override
    public boolean matches(List<QCRecord> recentRecords, BigDecimal actualMean, BigDecimal actualSd) {
        if (recentRecords.size() < 4) return false;
        int n = recentRecords.size();
        boolean allHigh = true;
        boolean allLow = true;
        for (int i = n - 4; i < n; i++) {
            BigDecimal z = Rule12s.calcZScore(recentRecords.get(i).getMeasureValue(), actualMean, actualSd);
            if (z == null) return false;
            if (z.compareTo(BigDecimal.valueOf(1)) < 0) allHigh = false;
            if (z.compareTo(BigDecimal.valueOf(-1)) > 0) allLow = false;
        }
        return allHigh || allLow;
    }

    @Override
    public String getSuggestion() {
        return "4-1s 失控: 连续4个点同侧超出均值±1SD，提示系统偏差，请检查校准/试剂";
    }
}
