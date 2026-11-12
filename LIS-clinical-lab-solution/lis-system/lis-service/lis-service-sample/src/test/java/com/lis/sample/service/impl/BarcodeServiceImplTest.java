package com.lis.sample.service.impl;

import com.lis.common.core.exception.BusinessException;
import com.lis.common.redis.RedisUtil;
import com.lis.sample.entity.BarcodeRule;
import com.lis.sample.entity.Sample;
import com.lis.sample.mapper.BarcodeRuleMapper;
import com.lis.sample.mapper.SampleDataMapper;
import com.lis.sample.testsupport.TableInfoTestSupport;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link BarcodeServiceImpl} 条码生成与校验单元测试。
 *
 * <p>被测类为普通 {@code @Service} (未继承 MyBatis-Plus {@code ServiceImpl}),
 * 依赖全部通过构造函数注入, 故可安全使用 {@code @InjectMocks}。</p>
 *
 * <p>覆盖: 双通道序列号 (Redis 主 / 数据库 fallback)、多种条码格式
 * (纯数字 / 年月日+序号 / 字母+数字 / 字母+年月日+序号)、
 * {@code validateBarcode} 唯一性、规则停用回退与规则更新校验。</p>
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class BarcodeServiceImplTest {

    @Mock
    private RedisUtil redisUtil;
    @Mock
    private SampleDataMapper sampleMapper;
    @Mock
    private BarcodeRuleMapper barcodeRuleMapper;
    @Mock
    private JdbcTemplate jdbcTemplate;

    @InjectMocks
    private BarcodeServiceImpl barcodeService;

    private static final String RULE_CODE = "SAMPLE";

    /** getEnabledRule / validateBarcode 内部构造 Lambda 条件, 需先注册表元信息 */
    @BeforeAll
    static void initTableInfo() {
        TableInfoTestSupport.ensureRegistered(BarcodeRule.class, Sample.class);
    }

    private static String today(String pattern) {
        return LocalDate.now().format(DateTimeFormatter.ofPattern(pattern));
    }

    private static BarcodeRule rule(String prefix, String datePattern, int seqLength, String strategy) {
        BarcodeRule r = new BarcodeRule();
        r.setId(1L);
        r.setRuleCode(RULE_CODE);
        r.setRuleName("标本条码规则");
        r.setPrefix(prefix);
        r.setDatePattern(datePattern);
        r.setSeqLength(seqLength);
        r.setSeqStrategy(strategy);
        r.setEnabled(1);
        return r;
    }

    private void givenEnabledRule(BarcodeRule r) {
        when(barcodeRuleMapper.selectOne(any())).thenReturn(r);
    }

    // ==================== 条码格式生成 ====================

    @Test
    @DisplayName("generateBarcode_pureNumericRule_returnsZeroPaddedSequence")
    void generateBarcode_pureNumericRule_returnsZeroPaddedSequence() {
        givenEnabledRule(rule("", "none", 10, "global"));
        when(redisUtil.incr(anyString())).thenReturn(1L);

        assertEquals("0000000001", barcodeService.generateBarcode());
    }

    @Test
    @DisplayName("generateBarcode_datePrefixedRule_returnsYyyyMMddPlusSequence")
    void generateBarcode_datePrefixedRule_returnsYyyyMMddPlusSequence() {
        givenEnabledRule(rule("", "yyyyMMdd", 4, "daily"));
        when(redisUtil.incr(anyString())).thenReturn(1L);

        assertEquals(today("yyyyMMdd") + "0001", barcodeService.generateBarcode(RULE_CODE));
    }

    @Test
    @DisplayName("generateBarcode_letterPrefixRule_returnsPrefixPlusSequence")
    void generateBarcode_letterPrefixRule_returnsPrefixPlusSequence() {
        givenEnabledRule(rule("S", "none", 10, "global"));
        when(redisUtil.isRedisAvailable()).thenReturn(true);
        when(redisUtil.incr(anyString())).thenReturn(7L);

        assertEquals("S0000000007", barcodeService.generateBarcode(RULE_CODE));
    }

    @Test
    @DisplayName("generateBarcode_letterAndDateRule_returnsPrefixDateSequence")
    void generateBarcode_letterAndDateRule_returnsPrefixDateSequence() {
        givenEnabledRule(rule("S", "yyyyMMdd", 4, "daily"));
        when(redisUtil.isRedisAvailable()).thenReturn(true);
        when(redisUtil.incr(anyString())).thenReturn(23L);

        assertEquals("S" + today("yyyyMMdd") + "0023", barcodeService.generateBarcode(RULE_CODE));
    }

    @Test
    @DisplayName("generateBarcode_shortYearDateRule_returnsYyMMddSequence")
    void generateBarcode_shortYearDateRule_returnsYyMMddSequence() {
        givenEnabledRule(rule("", "yyMMdd", 4, "daily"));
        when(redisUtil.incr(anyString())).thenReturn(1L);

        assertEquals(today("yyMMdd") + "0001", barcodeService.generateBarcode(RULE_CODE));
    }

    @Test
    @DisplayName("generateBarcode_nullSeqLength_defaultsToSixDigits")
    void generateBarcode_nullSeqLength_defaultsToSixDigits() {
        BarcodeRule r = rule("", "none", 6, "global");
        r.setSeqLength(null);
        givenEnabledRule(r);
        when(redisUtil.isRedisAvailable()).thenReturn(true);
        when(redisUtil.incr(anyString())).thenReturn(5L);

        assertEquals("000005", barcodeService.generateBarcode(RULE_CODE));
    }

    @Test
    @DisplayName("generateBarcode_unsupportedDatePattern_throwsBusinessException")
    void generateBarcode_unsupportedDatePattern_throwsBusinessException() {
        givenEnabledRule(rule("", "yyyy-MM-dd", 4, "daily"));

        BusinessException ex = assertThrows(BusinessException.class,
                () -> barcodeService.generateBarcode(RULE_CODE));

        assertTrue(ex.getMessage().contains("不支持的日期格式"));
    }

    // ==================== 规则停用回退 ====================

    @Test
    @DisplayName("generateBarcode_ruleDisabledOrMissing_throwsBusinessException")
    void generateBarcode_ruleDisabledOrMissing_throwsBusinessException() {
        // getEnabledRule 带 enabled=1 条件, 规则停用时查不到 → 返回 null
        when(barcodeRuleMapper.selectOne(any())).thenReturn(null);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> barcodeService.generateBarcode(RULE_CODE));

        assertTrue(ex.getMessage().contains("未找到启用的条码规则"));
        assertTrue(ex.getMessage().contains(RULE_CODE));
    }

    // ==================== 双通道: Redis 主 / DB fallback ====================

    @Test
    @DisplayName("generateBarcode_redisReturnsNull_fallsBackToDbMaxSequence")
    void generateBarcode_redisReturnsNull_fallsBackToDbMaxSequence() {
        givenEnabledRule(rule("", "none", 10, "global"));
        when(redisUtil.isRedisAvailable()).thenReturn(true);
        when(redisUtil.incr(anyString())).thenReturn(null);
        when(jdbcTemplate.queryForList(anyString(), eq(String.class), anyString()))
                .thenReturn(Collections.singletonList("0000000042"));

        assertEquals("0000000043", barcodeService.generateBarcode(RULE_CODE),
                "Redis 不可用时应取库内最大序号 + 1");
    }

    @Test
    @DisplayName("generateBarcode_redisThrows_fallsBackToDb")
    void generateBarcode_redisThrows_fallsBackToDb() {
        givenEnabledRule(rule("", "none", 10, "global"));
        when(redisUtil.isRedisAvailable()).thenReturn(true);
        when(redisUtil.incr(anyString())).thenThrow(new RuntimeException("Redis 连接失败"));
        when(jdbcTemplate.queryForList(anyString(), eq(String.class), anyString()))
                .thenReturn(Collections.singletonList("0000000100"));

        assertEquals("0000000101", barcodeService.generateBarcode(RULE_CODE));
    }

    @Test
    @DisplayName("generateBarcode_redisDownAndDbEmpty_startsFromOne")
    void generateBarcode_redisDownAndDbEmpty_startsFromOne() {
        givenEnabledRule(rule("", "none", 10, "global"));
        when(redisUtil.incr(anyString())).thenReturn(0L);
        when(jdbcTemplate.queryForList(anyString(), eq(String.class), eq("%")))
                .thenReturn(Collections.emptyList());

        assertEquals("0000000001", barcodeService.generateBarcode(RULE_CODE));
    }

    @Test
    @DisplayName("generateBarcode_redisDownAndDbThrows_startsFromOne")
    void generateBarcode_redisDownAndDbThrows_startsFromOne() {
        givenEnabledRule(rule("", "none", 10, "global"));
        when(redisUtil.incr(anyString())).thenReturn(null);
        when(jdbcTemplate.queryForList(anyString(), eq(String.class), eq("%")))
                .thenThrow(new RuntimeException("数据库不可用"));

        assertEquals("0000000001", barcodeService.generateBarcode(RULE_CODE),
                "Redis 与数据库双双不可用时应安全回退到 1, 不抛异常");
    }

    // ==================== validateBarcode 唯一性 ====================

    @Test
    @DisplayName("validateBarcode_barcodeNotUsed_returnsTrue")
    void validateBarcode_barcodeNotUsed_returnsTrue() {
        when(sampleMapper.selectCount(any())).thenReturn(0L);

        assertTrue(barcodeService.validateBarcode("S0000000001"));
    }

    @Test
    @DisplayName("validateBarcode_barcodeAlreadyExists_returnsFalse")
    void validateBarcode_barcodeAlreadyExists_returnsFalse() {
        when(sampleMapper.selectCount(any())).thenReturn(1L);

        assertFalse(barcodeService.validateBarcode("S0000000001"), "已占用的条码不应通过校验");
    }

    @Test
    @DisplayName("validateBarcode_countReturnsNull_treatedAsAvailable")
    void validateBarcode_countReturnsNull_treatedAsAvailable() {
        when(sampleMapper.selectCount(any())).thenReturn(null);

        assertTrue(barcodeService.validateBarcode("S0000000001"));
    }

    @Test
    @DisplayName("validateBarcode_nullOrEmptyBarcode_returnsFalse")
    void validateBarcode_nullOrEmptyBarcode_returnsFalse() {
        assertFalse(barcodeService.validateBarcode(null));
        assertFalse(barcodeService.validateBarcode(""));
    }

    // ==================== 规则更新校验 ====================

    @Test
    @DisplayName("updateRule_nullId_throwsBusinessException")
    void updateRule_nullId_throwsBusinessException() {
        BarcodeRule r = new BarcodeRule();

        BusinessException ex = assertThrows(BusinessException.class, () -> barcodeService.updateRule(r));

        assertTrue(ex.getMessage().contains("规则ID不能为空"));
    }

    @Test
    @DisplayName("updateRule_ruleNotFound_throwsBusinessException")
    void updateRule_ruleNotFound_throwsBusinessException() {
        BarcodeRule r = rule("S", "none", 6, "daily");
        when(barcodeRuleMapper.selectById(1L)).thenReturn(null);

        BusinessException ex = assertThrows(BusinessException.class, () -> barcodeService.updateRule(r));

        assertTrue(ex.getMessage().contains("条码规则不存在"));
    }

    @Test
    @DisplayName("updateRule_seqLengthOutOfRange_throwsBusinessException")
    void updateRule_seqLengthOutOfRange_throwsBusinessException() {
        when(barcodeRuleMapper.selectById(1L)).thenReturn(rule("S", "none", 6, "daily"));

        BarcodeRule tooLong = rule("S", "none", 11, "daily");
        BarcodeRule tooShort = rule("S", "none", 0, "daily");

        assertThrows(BusinessException.class, () -> barcodeService.updateRule(tooLong));
        assertThrows(BusinessException.class, () -> barcodeService.updateRule(tooShort));
    }

    @Test
    @DisplayName("updateRule_illegalDatePattern_throwsBusinessException")
    void updateRule_illegalDatePattern_throwsBusinessException() {
        when(barcodeRuleMapper.selectById(1L)).thenReturn(rule("S", "none", 6, "daily"));

        BusinessException ex = assertThrows(BusinessException.class,
                () -> barcodeService.updateRule(rule("S", "MM/dd", 6, "daily")));

        assertTrue(ex.getMessage().contains("日期格式必须是"));
    }

    @Test
    @DisplayName("updateRule_illegalSeqStrategy_throwsBusinessException")
    void updateRule_illegalSeqStrategy_throwsBusinessException() {
        when(barcodeRuleMapper.selectById(1L)).thenReturn(rule("S", "none", 6, "daily"));

        BusinessException ex = assertThrows(BusinessException.class,
                () -> barcodeService.updateRule(rule("S", "none", 6, "weekly")));

        assertTrue(ex.getMessage().contains("daily 或 global"));
    }

    @Test
    @DisplayName("updateRule_validRule_updatesWithoutThrowing")
    void updateRule_validRule_updatesWithoutThrowing() {
        when(barcodeRuleMapper.selectById(1L)).thenReturn(rule("S", "none", 6, "daily"));

        barcodeService.updateRule(rule("V", "yyyyMMdd", 8, "global"));

        verify(barcodeRuleMapper).update(isNull(), any());
    }
}
