package com.lis.qc.service.rule.rule;

import com.lis.qc.entity.QCLot;
import com.lis.qc.entity.QCRecord;
import com.lis.qc.service.rule.WestgardRule;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.List;

/**
 * Westgard R-4s 拒绝规则
 * <p>
 * 连续 2 个质控点极差超过 4SD (一点 +2SD，另一点 -2SD)。
 */
@Component
public class RuleR4s implements WestgardRule {

    @Override
    public String getRuleName() {
        return "R-4s";
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
        BigDecimal range = z1.subtract(z2).abs();
        // |z1 - z2| >= 4 即两值跨方向合计至少 4SD (Westgard R-4s)
        return range.compareTo(BigDecimal.valueOf(4)) >= 0;
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
        BigDecimal range = z1.subtract(z2).abs();
        return range.compareTo(BigDecimal.valueOf(4)) >= 0;
    }

    @Override
    public String getSuggestion() {
        return "R-4s 失控: 连续2个点极差超过4SD，提示存在随机误差，请检查仪器状态/电源/温度";
    }
}
