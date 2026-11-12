package com.lis.qc.service.rule.rule;

import com.lis.qc.entity.QCLot;
import com.lis.qc.entity.QCRecord;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Westgard 六条规则 + calcZScore 单元测试
 * <p>
 * 靶值=100, SD=5 => z=(value-100)/5
 */
class WestgardRulesTest {

    private final QCLot lot = buildLot();

    private QCLot buildLot() {
        QCLot l = new QCLot();
        l.setTargetValue(BigDecimal.valueOf(100));
        l.setSd(BigDecimal.valueOf(5));
        return l;
    }

    private QCRecord rec(double v) {
        QCRecord r = new QCRecord();
        r.setMeasureValue(BigDecimal.valueOf(v));
        return r;
    }

    private List<QCRecord> list(double... vs) {
        List<QCRecord> rs = new ArrayList<>();
        for (double v : vs) rs.add(rec(v));
        return rs;
    }

    // ---------------- calcZScore ----------------
    @Test
    void calcZScore_normal() {
        assertEquals(0, Rule12s.calcZScore(rec(100), lot).compareTo(BigDecimal.valueOf(0)));
        assertEquals(0, Rule12s.calcZScore(rec(112.5), lot).compareTo(BigDecimal.valueOf(2.5)));
    }

    @Test
    void calcZScore_nullWhenSdZero() {
        QCLot zero = new QCLot();
        zero.setTargetValue(BigDecimal.valueOf(100));
        zero.setSd(BigDecimal.ZERO);
        assertNull(Rule12s.calcZScore(rec(100), zero));
    }

    @Test
    void calcZScore_nullWhenMeasureNull() {
        QCRecord r = new QCRecord();
        assertNull(Rule12s.calcZScore(r, lot));
    }

    // ---------------- 1-3s (REJECT) ----------------
    @Test
    void rule13s_match_when_z_ge_3() {
        assertTrue(new Rule13s().matches(list(117.5), lot));   // z=3.5
    }

    @Test
    void rule13s_noMatch_when_z_lt_3() {
        assertFalse(new Rule13s().matches(list(114), lot));    // z=2.8
        assertFalse(new Rule13s().matches(list(100), lot));    // z=0
    }

    @Test
    void rule13s_noMatch_whenNullValue() {
        QCRecord r = new QCRecord(); // measureValue = null
        assertFalse(new Rule13s().matches(List.of(r), lot));
    }

    // ---------------- 2-2s (REJECT) ----------------
    @Test
    void rule22s_match_sameSide_both_gt_2sd() {
        assertTrue(new Rule22s().matches(list(111, 112), lot)); // z=2.2, 2.4 同侧(均>2)
    }

    @Test
    void rule22s_noMatch_oppositeSide() {
        assertFalse(new Rule22s().matches(list(112, 88), lot)); // z=2.4, -2.4 异侧
    }

    @Test
    void rule22s_noMatch_insufficientPoints() {
        assertFalse(new Rule22s().matches(list(110), lot));
    }

    // ---------------- R-4s (REJECT) ----------------
    @Test
    void ruleR4s_match_when_range_gt_4sd() {
        assertTrue(new RuleR4s().matches(list(110.5, 89.5), lot)); // z=2.1,-2.1 range=4.2
    }

    @Test
    void ruleR4s_noMatch_when_range_le_4sd() {
        assertFalse(new RuleR4s().matches(list(105, 107), lot));  // z=1,1.4 range=0.4
    }

    // ---------------- 1-2s (WARNING) ----------------
    @Test
    void rule12s_match_when_2_lt_z_lt_3() {
        assertTrue(new Rule12s().matches(list(112.5), lot)); // z=2.5
    }

    @Test
    void rule12s_noMatch_when_z_ge_3() {
        assertTrue(new Rule12s().matches(list(117.5), lot)); // 设计: 1-2s 不设上限, 即使 z≥3 也报警
    }

    @Test
    void rule12s_noMatch_when_z_le_2() {
        assertFalse(new Rule12s().matches(list(109), lot));   // z=1.8
    }

    // ---------------- 4-1s (WARNING) ----------------
    @Test
    void rule41s_match_4_consecutive_sameSide_gt_1sd() {
        assertTrue(new Rule41s().matches(list(106, 107, 108, 109), lot)); // z=1.2..1.8
    }

    @Test
    void rule41s_noMatch_oneCrosses() {
        assertFalse(new Rule41s().matches(list(106, 107, 108, 99), lot)); // 最后一个 z<1
    }

    @Test
    void rule41s_noMatch_insufficientPoints() {
        assertFalse(new Rule41s().matches(list(106, 107, 108), lot));
    }

    // ---------------- 10x (WARNING) ----------------
    @Test
    void rule10x_match_10_sameSide() {
        assertTrue(new Rule10x().matches(list(101, 102, 103, 104, 105, 106, 107, 108, 109, 110), lot));
    }

    @Test
    void rule10x_noMatch_oneCrossesZero() {
        assertFalse(new Rule10x().matches(list(101, 102, 103, 104, 105, 106, 107, 108, 109, 99), lot));
    }

    @Test
    void rule10x_noMatch_insufficientPoints() {
        assertFalse(new Rule10x().matches(list(101, 102, 103, 104, 105, 106, 107, 108, 109), lot));
    }
}
