package com.lis.qc.service.rule.rule;

import com.lis.qc.entity.QCLot;
import com.lis.qc.entity.QCRecord;
import com.lis.qc.service.rule.WestgardRule;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

/**
 * Westgard 1-2s 警告规则
 * <p>
 * 最近一个质控点超出均值 ± 2SD。仅警告，不拒绝。
 */
@Component
public class Rule12s implements WestgardRule {

    @Override
    public String getRuleName() {
        return "1-2s";
    }

    @Override
    public String getRuleType() {
        return TYPE_WARNING;
    }

    @Override
    public boolean matches(List<QCRecord> recentRecords, QCLot lot) {
        if (recentRecords.isEmpty()) return false;
        QCRecord last = recentRecords.get(recentRecords.size() - 1);
        BigDecimal z = calcZScore(last, lot);
        if (z == null) return false;
        // |z| >= 2 触发警告 (不设上限: 若 1-3s 等 REJECT 规则被禁用, 超出 3SD 也应由 1-2s 报警)
        return z.abs().compareTo(BigDecimal.valueOf(2)) >= 0;
    }

    @Override
    public boolean matches(List<QCRecord> recentRecords, BigDecimal actualMean, BigDecimal actualSd) {
        if (recentRecords.isEmpty()) return false;
        QCRecord last = recentRecords.get(recentRecords.size() - 1);
        BigDecimal z = calcZScore(last.getMeasureValue(), actualMean, actualSd);
        if (z == null) return false;
        return z.abs().compareTo(BigDecimal.valueOf(2)) >= 0;
    }

    @Override
    public String getSuggestion() {
        return "1-2s 警告: 超出均值±2SD，请关注后续质控点，检查试剂/仪器状态";
    }

    static BigDecimal calcZScore(QCRecord r, QCLot lot) {
        if (r.getMeasureValue() == null || lot.getTargetValue() == null
                || lot.getSd() == null || lot.getSd().compareTo(BigDecimal.ZERO) == 0) {
            return null;
        }
        return r.getMeasureValue()
                .subtract(lot.getTargetValue())
                .divide(lot.getSd(), 4, RoundingMode.HALF_UP);
    }

    /** 使用实际均值和SD计算Z分数 */
    static BigDecimal calcZScore(BigDecimal measureValue, BigDecimal mean, BigDecimal sd) {
        if (measureValue == null || mean == null || sd == null || sd.compareTo(BigDecimal.ZERO) == 0) {
            return null;
        }
        return measureValue.subtract(mean).divide(sd, 4, RoundingMode.HALF_UP);
    }
}
