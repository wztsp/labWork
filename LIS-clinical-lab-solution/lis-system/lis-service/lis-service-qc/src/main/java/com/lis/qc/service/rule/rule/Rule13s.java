package com.lis.qc.service.rule.rule;

import com.lis.qc.entity.QCLot;
import com.lis.qc.entity.QCRecord;
import com.lis.qc.service.rule.WestgardRule;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.List;

/**
 * Westgard 1-3s 拒绝规则
 * <p>
 * 最近一个质控点超出均值 ± 3SD，立即失控。
 */
@Component
public class Rule13s implements WestgardRule {

    @Override
    public String getRuleName() {
        return "1-3s";
    }

    @Override
    public boolean matches(List<QCRecord> recentRecords, QCLot lot) {
        if (recentRecords.isEmpty()) return false;
        QCRecord last = recentRecords.get(recentRecords.size() - 1);
        BigDecimal z = Rule12s.calcZScore(last, lot);
        if (z == null) return false;
        return z.abs().compareTo(BigDecimal.valueOf(3)) >= 0;
    }

    @Override
    public boolean matches(List<QCRecord> recentRecords, BigDecimal actualMean, BigDecimal actualSd) {
        if (recentRecords.isEmpty()) return false;
        QCRecord last = recentRecords.get(recentRecords.size() - 1);
        BigDecimal z = Rule12s.calcZScore(last.getMeasureValue(), actualMean, actualSd);
        if (z == null) return false;
        return z.abs().compareTo(BigDecimal.valueOf(3)) >= 0;
    }

    @Override
    public String getSuggestion() {
        return "1-3s 失控: 超出均值±3SD，可能存在随机误差，请检查仪器/试剂/校准";
    }
}
