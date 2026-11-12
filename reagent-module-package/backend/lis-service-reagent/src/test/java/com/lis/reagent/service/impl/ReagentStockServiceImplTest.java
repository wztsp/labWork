package com.lis.reagent.service.impl;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.lis.common.core.exception.BusinessException;
import com.lis.reagent.entity.Reagent;
import com.lis.reagent.entity.ReagentStock;
import com.lis.reagent.entity.ReagentStockRecord;
import com.lis.reagent.mapper.ReagentMapper;
import com.lis.reagent.mapper.ReagentStockRecordMapper;
import com.lis.reagent.testsupport.TableInfoTestSupport;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.ArgumentMatchers;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link ReagentStockServiceImpl} 试剂库存单元测试。
 *
 * <p><b>测试策略</b>: 被测类继承 MyBatis-Plus {@code ServiceImpl},
 * 采用 {@code spy(new ReagentStockServiceImpl(...))} 并桩化父类
 * {@code list()/getById()/updateById()}, 避开 {@code baseMapper} 为空导致的 NPE。
 * 同时通过 {@code TableInfoTestSupport} 预注册表元信息, 使被测方法内部的
 * {@code LambdaQueryWrapper} 能正常构造。</p>
 *
 * <p><b>覆盖</b>: {@code outStockFifo} 先进先出与跨批次拆分、库存不足抛错、
 * 批次耗尽状态流转、{@code outStock} 单批次校验、{@code listBelowSafetyStock}
 * 安全库存预警、{@code lockExpiredStock} 过期锁定。</p>
 *
 * <p>金额/数量断言一律使用 {@link BigDecimal#compareTo}, 禁止 {@code double}。</p>
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ReagentStockServiceImplTest {

    @Mock
    private ReagentStockRecordMapper recordMapper;
    @Mock
    private ReagentMapper reagentMapper;

    private ReagentStockServiceImpl service;

    private static final Long REAGENT_ID = 9L;
    private static final Long OPERATOR_ID = 66L;
    private static final String OPERATOR = "王五";

    @BeforeAll
    static void initTableInfo() {
        TableInfoTestSupport.ensureRegistered(ReagentStock.class, Reagent.class);
    }

    @BeforeEach
    void setUp() {
        service = spy(new ReagentStockServiceImpl(recordMapper, reagentMapper));
        doReturn(true).when(service).updateById(any(ReagentStock.class));
    }

    // ==================== 夹具 ====================

    private static BigDecimal bd(String v) {
        return new BigDecimal(v);
    }

    private static ReagentStock stock(Long id, String lotNo, String quantity, LocalDate expiry, int status) {
        ReagentStock s = new ReagentStock();
        s.setId(id);
        s.setReagentId(REAGENT_ID);
        s.setLotNo(lotNo);
        s.setQuantity(bd(quantity));
        s.setExpiryDate(expiry);
        s.setStatus(status);
        return s;
    }

    /** 构造试剂夹具; minStock 即最低(安全)库存阈值, 原 safetyStock 字段已废弃 */
    private static Reagent reagent(Long id, String minStock) {
        Reagent r = new Reagent();
        r.setId(id);
        r.setReagentName("试剂" + id);
        r.setMinStock(minStock == null ? null : bd(minStock));
        r.setStatus(1);
        return r;
    }

    /** 桩化父类 list(Wrapper) —— 必须显式指定泛型, 否则与 list(IPage) 重载歧义 */
    private void givenStockList(List<ReagentStock> stocks) {
        doReturn(stocks).when(service).list(ArgumentMatchers.<Wrapper<ReagentStock>>any());
    }

    private List<ReagentStockRecord> capturedRecords() {
        ArgumentCaptor<ReagentStockRecord> captor = ArgumentCaptor.forClass(ReagentStockRecord.class);
        verify(recordMapper, times(1)).insert(captor.capture());
        return captor.getAllValues();
    }

    // ==================== outStockFifo ====================

    @Nested
    @DisplayName("outStockFifo: 按到期日先进先出出库")
    class OutStockFifo {

        @Test
        @DisplayName("outStockFifo_singleBatchSufficient_consumesFromEarliestBatch")
        void outStockFifo_singleBatchSufficient_consumesFromEarliestBatch() {
            ReagentStock early = stock(1L, "LOT-A", "20", LocalDate.now().plusDays(10), 1);
            ReagentStock later = stock(2L, "LOT-B", "50", LocalDate.now().plusDays(90), 1);
            givenStockList(Arrays.asList(early, later));

            List<ReagentStockRecord> records = service.outStockFifo(
                    REAGENT_ID, bd("8"), OPERATOR_ID, OPERATOR, "SO-001", "常规出库");

            assertAll(
                    () -> assertEquals(1, records.size(), "库存充足时只应消耗最早到期批次"),
                    () -> assertEquals(0, early.getQuantity().compareTo(bd("12"))),
                    () -> assertEquals(0, later.getQuantity().compareTo(bd("50")), "后续批次不应被动用"),
                    () -> assertEquals(1L, records.get(0).getStockId())
            );
            verify(service, times(1)).updateById(early);
            verify(service, never()).updateById(later);
        }

        @Test
        @DisplayName("outStockFifo_crossBatch_splitsInFifoOrder")
        void outStockFifo_crossBatch_splitsInFifoOrder() {
            ReagentStock early = stock(1L, "LOT-A", "5", LocalDate.now().plusDays(10), 1);
            ReagentStock later = stock(2L, "LOT-B", "50", LocalDate.now().plusDays(90), 1);
            givenStockList(Arrays.asList(early, later));

            List<ReagentStockRecord> records = service.outStockFifo(
                    REAGENT_ID, bd("8"), OPERATOR_ID, OPERATOR, "SO-002", null);

            assertAll(
                    () -> assertEquals(2, records.size(), "需求超过首批次时应跨批次拆分"),
                    () -> assertEquals(1L, records.get(0).getStockId(), "先扣最早到期批次"),
                    () -> assertEquals(2L, records.get(1).getStockId()),
                    () -> assertEquals(0, records.get(0).getQuantity().compareTo(bd("-5"))),
                    () -> assertEquals(0, records.get(1).getQuantity().compareTo(bd("-3"))),
                    () -> assertEquals(0, early.getQuantity().compareTo(BigDecimal.ZERO)),
                    () -> assertEquals(0, later.getQuantity().compareTo(bd("47")))
            );
        }

        @Test
        @DisplayName("outStockFifo_batchExactlyDepleted_marksStatusDepleted")
        void outStockFifo_batchExactlyDepleted_marksStatusDepleted() {
            ReagentStock only = stock(1L, "LOT-A", "5", LocalDate.now().plusDays(10), 1);
            givenStockList(Collections.singletonList(only));

            service.outStockFifo(REAGENT_ID, bd("5"), OPERATOR_ID, OPERATOR, "SO-003", null);

            assertEquals(2, only.getStatus(), "批次恰好扣完应置为已耗尽(2)");
            assertEquals(0, only.getQuantity().compareTo(BigDecimal.ZERO));
        }

        @Test
        @DisplayName("outStockFifo_partialConsumption_keepsStatusAvailable")
        void outStockFifo_partialConsumption_keepsStatusAvailable() {
            ReagentStock only = stock(1L, "LOT-A", "5", LocalDate.now().plusDays(10), 1);
            givenStockList(Collections.singletonList(only));

            service.outStockFifo(REAGENT_ID, bd("2"), OPERATOR_ID, OPERATOR, "SO-004", null);

            assertEquals(1, only.getStatus(), "未扣完的批次应保持可用(1)");
        }

        @Test
        @DisplayName("outStockFifo_insufficientStock_throwsBusinessExceptionWithGap")
        void outStockFifo_insufficientStock_throwsBusinessExceptionWithGap() {
            givenStockList(Collections.singletonList(
                    stock(1L, "LOT-A", "5", LocalDate.now().plusDays(10), 1)));

            BusinessException ex = assertThrows(BusinessException.class, () -> service.outStockFifo(
                    REAGENT_ID, bd("8"), OPERATOR_ID, OPERATOR, "SO-005", null));

            assertTrue(ex.getMessage().contains("库存不足"));
            assertTrue(ex.getMessage().contains("3"), "异常信息应携带缺口数量");
        }

        @Test
        @DisplayName("outStockFifo_noAvailableBatch_throwsBusinessException")
        void outStockFifo_noAvailableBatch_throwsBusinessException() {
            // 过期 / 停用批次已在 SQL 层被 status=1 AND expiry_date > today 过滤, 查询结果为空
            givenStockList(Collections.emptyList());

            BusinessException ex = assertThrows(BusinessException.class, () -> service.outStockFifo(
                    REAGENT_ID, bd("1"), OPERATOR_ID, OPERATOR, "SO-006", null));

            assertTrue(ex.getMessage().contains("库存不足"));
            verify(recordMapper, never()).insert(any(ReagentStockRecord.class));
        }

        @Test
        @DisplayName("outStockFifo_expiredBatchFilteredBySqlCondition_notConsumed")
        void outStockFifo_expiredBatchFilteredBySqlCondition_notConsumed() {
            // 断言过滤条件真实写入 wrapper: status=1 且 expiry_date > 今天, 并按到期日升序
            givenStockList(Collections.singletonList(
                    stock(1L, "LOT-A", "10", LocalDate.now().plusDays(10), 1)));

            service.outStockFifo(REAGENT_ID, bd("1"), OPERATOR_ID, OPERATOR, "SO-007", null);

            ArgumentCaptor<Wrapper<ReagentStock>> captor = ArgumentCaptor.forClass(Wrapper.class);
            verify(service).list(captor.capture());
            String sql = captor.getValue().getSqlSegment();
            assertAll(
                    () -> assertTrue(sql.contains("reagent_id ="), "应按试剂过滤"),
                    () -> assertTrue(sql.contains("status ="), "应过滤不可用批次"),
                    () -> assertTrue(sql.contains("expiry_date >"), "过期批次必须被排除"),
                    () -> assertTrue(sql.contains("ORDER BY expiry_date ASC"), "FIFO 依赖到期日升序")
            );
        }

        @Test
        @DisplayName("outStockFifo_stockRecordCarriesBeforeAndAfterQuantity")
        void outStockFifo_stockRecordCarriesBeforeAndAfterQuantity() {
            ReagentStock only = stock(1L, "LOT-A", "20", LocalDate.now().plusDays(10), 1);
            givenStockList(Collections.singletonList(only));

            service.outStockFifo(REAGENT_ID, bd("8"), OPERATOR_ID, OPERATOR, "SO-008", "备注");

            ReagentStockRecord rec = capturedRecords().get(0);
            assertAll(
                    () -> assertEquals(2, rec.getRecordType(), "出库流水类型应为 2"),
                    () -> assertEquals(0, rec.getQuantity().compareTo(bd("-8")), "出库数量应为负数"),
                    () -> assertEquals(0, rec.getBeforeQty().compareTo(bd("20"))),
                    () -> assertEquals(0, rec.getAfterQty().compareTo(bd("12"))),
                    () -> assertEquals(OPERATOR_ID, rec.getOperatorId()),
                    () -> assertEquals(OPERATOR, rec.getOperatorName()),
                    () -> assertEquals("SO-008", rec.getSourceNo())
            );
        }

        @Test
        @DisplayName("outStockFifo_nonPositiveQuantity_throwsBusinessException")
        void outStockFifo_nonPositiveQuantity_throwsBusinessException() {
            assertThrows(BusinessException.class, () -> service.outStockFifo(
                    REAGENT_ID, BigDecimal.ZERO, OPERATOR_ID, OPERATOR, "SO-009", null));
            assertThrows(BusinessException.class, () -> service.outStockFifo(
                    REAGENT_ID, bd("-1"), OPERATOR_ID, OPERATOR, "SO-009", null));
            assertThrows(BusinessException.class, () -> service.outStockFifo(
                    REAGENT_ID, null, OPERATOR_ID, OPERATOR, "SO-009", null));

            verify(service, never()).list(ArgumentMatchers.<Wrapper<ReagentStock>>any());
        }
    }

    // ==================== outStock 单批次 ====================

    @Nested
    @DisplayName("outStock: 指定批次出库")
    class OutStockByBatch {

        @Test
        @DisplayName("outStock_validBatch_deductsAndReturnsRecord")
        void outStock_validBatch_deductsAndReturnsRecord() {
            ReagentStock s = stock(1L, "LOT-A", "20", LocalDate.now().plusDays(10), 1);
            doReturn(s).when(service).getById(any(Serializable.class));

            ReagentStockRecord rec = service.outStock(1L, bd("5"), OPERATOR_ID, OPERATOR, "SO-010", null);

            assertEquals(0, s.getQuantity().compareTo(bd("15")));
            assertEquals(0, rec.getQuantity().compareTo(bd("-5")));
        }

        @Test
        @DisplayName("outStock_batchNotFound_throwsBusinessException")
        void outStock_batchNotFound_throwsBusinessException() {
            doReturn(null).when(service).getById(any(Serializable.class));

            BusinessException ex = assertThrows(BusinessException.class,
                    () -> service.outStock(99L, bd("1"), OPERATOR_ID, OPERATOR, null, null));

            assertTrue(ex.getMessage().contains("库存批次不存在"));
        }

        @Test
        @DisplayName("outStock_lockedOrDepletedBatch_throwsBusinessException")
        void outStock_lockedOrDepletedBatch_throwsBusinessException() {
            doReturn(stock(1L, "LOT-A", "20", LocalDate.now().plusDays(10), 0))
                    .when(service).getById(any(Serializable.class));

            BusinessException ex = assertThrows(BusinessException.class,
                    () -> service.outStock(1L, bd("1"), OPERATOR_ID, OPERATOR, null, null));

            assertTrue(ex.getMessage().contains("已被锁定"));
        }

        @Test
        @DisplayName("outStock_quantityExceedsBatch_throwsBusinessException")
        void outStock_quantityExceedsBatch_throwsBusinessException() {
            doReturn(stock(1L, "LOT-A", "3", LocalDate.now().plusDays(10), 1))
                    .when(service).getById(any(Serializable.class));

            BusinessException ex = assertThrows(BusinessException.class,
                    () -> service.outStock(1L, bd("5"), OPERATOR_ID, OPERATOR, null, null));

            assertTrue(ex.getMessage().contains("批次库存不足"));
            verify(recordMapper, never()).insert(any(ReagentStockRecord.class));
        }
    }

    // ==================== 安全库存 / 过期锁定 ====================

    @Nested
    @DisplayName("安全库存预警与过期锁定")
    class SafetyStockAndExpiry {

        @Test
        @DisplayName("listBelowSafetyStock_totalBelowThreshold_returnsAlert")
        void listBelowSafetyStock_totalBelowThreshold_returnsAlert() {
            when(reagentMapper.selectList(any()))
                    .thenReturn(Collections.singletonList(reagent(REAGENT_ID, "100")));
            givenStockList(Arrays.asList(
                    stock(1L, "LOT-A", "30", LocalDate.now().plusDays(10), 1),
                    stock(2L, "LOT-B", "20", LocalDate.now().plusDays(20), 1)));

            List<ReagentStock> alerts = service.listBelowSafetyStock();

            assertAll(
                    () -> assertEquals(1, alerts.size()),
                    () -> assertEquals(REAGENT_ID, alerts.get(0).getReagentId()),
                    () -> assertEquals(0, alerts.get(0).getQuantity().compareTo(bd("50")),
                            "预警对象应携带各批次汇总量"),
                    () -> assertTrue(alerts.get(0).getLocation().contains("100"), "应回显安全库存阈值")
            );
        }

        @Test
        @DisplayName("listBelowSafetyStock_totalAboveThreshold_returnsEmpty")
        void listBelowSafetyStock_totalAboveThreshold_returnsEmpty() {
            when(reagentMapper.selectList(any()))
                    .thenReturn(Collections.singletonList(reagent(REAGENT_ID, "10")));
            givenStockList(Collections.singletonList(
                    stock(1L, "LOT-A", "30", LocalDate.now().plusDays(10), 1)));

            assertTrue(service.listBelowSafetyStock().isEmpty());
        }

        @Test
        @DisplayName("listBelowSafetyStock_totalEqualsThreshold_notAlerted")
        void listBelowSafetyStock_totalEqualsThreshold_notAlerted() {
            when(reagentMapper.selectList(any()))
                    .thenReturn(Collections.singletonList(reagent(REAGENT_ID, "30")));
            givenStockList(Collections.singletonList(
                    stock(1L, "LOT-A", "30", LocalDate.now().plusDays(10), 1)));

            assertTrue(service.listBelowSafetyStock().isEmpty(), "等于安全库存不算低于安全库存");
        }

        @Test
        @DisplayName("listBelowSafetyStock_noStockRecord_alertsWithZeroQuantity")
        void listBelowSafetyStock_noStockRecord_alertsWithZeroQuantity() {
            when(reagentMapper.selectList(any()))
                    .thenReturn(Collections.singletonList(reagent(REAGENT_ID, "100")));
            givenStockList(Collections.emptyList());

            List<ReagentStock> alerts = service.listBelowSafetyStock();

            assertEquals(1, alerts.size());
            assertEquals(0, alerts.get(0).getQuantity().compareTo(BigDecimal.ZERO));
        }

        @Test
        @DisplayName("lockExpiredStock_expiredBatches_setStatusZeroAndReturnCount")
        void lockExpiredStock_expiredBatches_setStatusZeroAndReturnCount() {
            ReagentStock e1 = stock(1L, "LOT-A", "10", LocalDate.now().minusDays(1), 1);
            ReagentStock e2 = stock(2L, "LOT-B", "10", LocalDate.now(), 1);
            givenStockList(Arrays.asList(e1, e2));

            int count = service.lockExpiredStock();

            assertAll(
                    () -> assertEquals(2, count),
                    () -> assertEquals(0, e1.getStatus(), "过期批次应锁定为 0"),
                    () -> assertEquals(0, e2.getStatus(), "当天到期也应锁定")
            );
            verify(service, times(2)).updateById(any(ReagentStock.class));
        }

        @Test
        @DisplayName("lockExpiredStock_noExpiredBatch_returnsZero")
        void lockExpiredStock_noExpiredBatch_returnsZero() {
            givenStockList(Collections.emptyList());

            assertEquals(0, service.lockExpiredStock());
            verify(service, never()).updateById(any(ReagentStock.class));
        }

        @Test
        @DisplayName("listNearExpiry_buildsWindowBetweenTodayAndThreshold")
        void listNearExpiry_buildsWindowBetweenTodayAndThreshold() {
            ReagentStock near = stock(1L, "LOT-A", "10", LocalDate.now().plusDays(15), 1);
            givenStockList(Collections.singletonList(near));

            List<ReagentStock> result = service.listNearExpiry(30);

            assertEquals(1, result.size());
            ArgumentCaptor<Wrapper<ReagentStock>> captor = ArgumentCaptor.forClass(Wrapper.class);
            verify(service).list(captor.capture());
            String sql = captor.getValue().getSqlSegment();
            assertTrue(sql.contains("expiry_date <="), "近效期查询需有上界");
            assertTrue(sql.contains("expiry_date >"), "近效期查询需排除已过期批次");
        }
    }
}
