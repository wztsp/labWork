package com.lis.qc.service.rule;

import com.lis.qc.entity.QCLot;
import com.lis.qc.entity.QCRecord;
import com.lis.qc.mapper.QcRuleMapper;
import com.lis.qc.service.rule.rule.Rule10x;
import com.lis.qc.service.rule.rule.Rule12s;
import com.lis.qc.service.rule.rule.Rule13s;
import com.lis.qc.service.rule.rule.Rule22s;
import com.lis.qc.service.rule.rule.Rule41s;
import com.lis.qc.service.rule.rule.RuleR4s;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * WestgardRuleEngine 单元测试
 * <p>
 * 不依赖 Spring，直接构造规则实例 + 模拟 QcRuleMapper。
 * 靶值=100, SD=5 => z=(value-100)/5
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class WestgardRuleEngineTest {

    @Mock
    private QcRuleMapper qcRuleMapper;

    private WestgardRuleEngine engine;
    private QCLot lot;

    @BeforeEach
    void setUp() {
        // 模拟数据库无启用规则配置 -> 引擎回退使用全部规则
        when(qcRuleMapper.selectList(any())).thenReturn(Collections.emptyList());
        engine = new WestgardRuleEngine(
                List.of(new Rule13s(), new Rule22s(), new RuleR4s(),
                        new Rule12s(), new Rule41s(), new Rule10x()),
                qcRuleMapper);
        lot = new QCLot();
        lot.setTargetValue(BigDecimal.valueOf(100));
        lot.setSd(BigDecimal.valueOf(5));
    }

    private QCRecord rec(double measure) {
        QCRecord r = new QCRecord();
        r.setMeasureValue(BigDecimal.valueOf(measure));
        return r;
    }

    @Test
    void evaluate_emptyRecords_shouldRejectPreCheck() {
        QCResult result = engine.evaluate(Collections.emptyList(), lot);
        assertFalse(result.isAccepted());
        assertEquals(QCResult.LEVEL_REJECT, result.getLevel());
        assertEquals("PreCheck", result.getRuleName());
    }

    @Test
    void evaluate_nullLot_shouldRejectPreCheck() {
        QCResult result = engine.evaluate(List.of(rec(100)), null);
        assertFalse(result.isAccepted());
        assertEquals("PreCheck", result.getRuleName());
    }

    @Test
    void evaluate_13s_shouldReject() {
        // z = (117.5-100)/5 = 3.5 >= 3 -> 1-3s 失控
        QCResult result = engine.evaluate(List.of(rec(117.5)), lot);
        assertFalse(result.isAccepted());
        assertEquals(QCResult.LEVEL_REJECT, result.getLevel());
        assertEquals("1-3s", result.getRuleName());
    }

    @Test
    void evaluate_12s_warning_shouldAccept() {
        // z = (112.5-100)/5 = 2.5 -> 2<|z|<3 -> 仅警告
        QCResult result = engine.evaluate(List.of(rec(112.5)), lot);
        assertTrue(result.isAccepted());
        assertEquals(QCResult.LEVEL_WARNING, result.getLevel());
        assertEquals("1-2s", result.getRuleName());
    }

    @Test
    void evaluate_inControl_shouldAccept() {
        QCResult result = engine.evaluate(List.of(rec(100)), lot);
        assertTrue(result.isAccepted());
        assertEquals(QCResult.LEVEL_ACCEPT, result.getLevel());
    }
}
