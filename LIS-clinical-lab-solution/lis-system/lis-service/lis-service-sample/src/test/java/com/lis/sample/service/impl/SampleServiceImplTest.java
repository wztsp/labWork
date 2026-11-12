package com.lis.sample.service.impl;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.lis.api.dto.SampleCollectDTO;
import com.lis.api.dto.SampleReceiveDTO;
import com.lis.api.vo.SampleVO;
import com.lis.common.core.exception.BusinessException;
import com.lis.sample.entity.OrderItem;
import com.lis.sample.entity.Sample;
import com.lis.sample.entity.SampleOrderItem;
import com.lis.sample.mapper.SampleInstrumentItemMapMapper;
import com.lis.sample.mapper.OrderItemMapper;
import com.lis.sample.mapper.SampleOrderItemMapper;
import com.lis.sample.service.BarcodeService;
import com.lis.sample.testsupport.TableInfoTestSupport;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentMatchers;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link SampleServiceImpl} 标本状态机单元测试。
 *
 * <p><b>测试策略</b>: {@code SampleServiceImpl} 继承 MyBatis-Plus {@code ServiceImpl},
 * 其 {@code list()/getOne()/updateById()} 依赖 {@code TableInfo} 静态注册,
 * 直接 {@code @InjectMocks} 会 NPE。故统一采用
 * {@code spy(new SampleServiceImpl(...))} + {@code doReturn(...).when(spy).getOne(...)}
 * 桩化父类方法, 绕开 MyBatis-Plus 静态初始化。</p>
 *
 * <p><b>覆盖范围</b>: 采集 → 送检 → 签收 → 核收 的正向流转,
 * 以及 {@code cancelDeliver} / {@code cancelSignOff} 逆向操作的前置状态校验。
 * 所有非法状态迁移必须抛 {@link BusinessException}。</p>
 *
 * <p>注: 被测方法内部通过 {@code this.getOne(wrapper)} (虚调用) 取标本, 可被 spy 拦截;
 * 依赖 {@code super.getById()} 的分支 (invokespecial, spy 无法拦截) 不在本类覆盖范围。</p>
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class SampleServiceImplTest {

    @Mock
    private RabbitTemplate rabbitTemplate;
    @Mock
    private OrderItemMapper orderItemMapper;
    @Mock
    private SampleInstrumentItemMapMapper instrumentItemMapMapper;
    @Mock
    private SampleOrderItemMapper sampleOrderItemMapper;
    @Mock
    private JdbcTemplate jdbcTemplate;
    @Mock
    private BarcodeService barcodeService;

    private SampleServiceImpl service;

    private static final String BARCODE = "S20260731000001";
    private static final Long USER_ID = 88L;

    /**
     * 被测方法内部构造 {@code LambdaQueryWrapper<Sample>}, 需先注册 MyBatis-Plus 表元信息,
     * 否则会在 wrapper 构造阶段抛 {@code MybatisPlusException}, 根本进不到业务分支。
     */
    @BeforeAll
    static void initTableInfo() {
        TableInfoTestSupport.ensureRegistered(Sample.class, OrderItem.class, SampleOrderItem.class);
    }

    @BeforeEach
    void setUp() {
        service = spy(new SampleServiceImpl(rabbitTemplate, orderItemMapper, instrumentItemMapMapper,
                sampleOrderItemMapper, jdbcTemplate, barcodeService));
        // 桩化 MyBatis-Plus 父类写操作, 避免触碰 baseMapper
        doReturn(true).when(service).updateById(any(Sample.class));
        doReturn(true).when(service).update(ArgumentMatchers.<Wrapper<Sample>>any());
    }

    /** 构造指定状态的标本; orderId / collectorId 留空以避免 toVO 触发额外查询 */
    private Sample sample(int status) {
        Sample s = new Sample();
        s.setId(1001L);
        s.setSampleBarcode(BARCODE);
        s.setStatus(status);
        s.setPatientId(500L);
        s.setSampleType("血清");
        return s;
    }

    /** 桩化 this.getOne(wrapper) 的返回值 */
    private void givenSampleFound(Sample s) {
        doReturn(s).when(service).getOne(ArgumentMatchers.<Wrapper<Sample>>any());
    }

    private static SampleCollectDTO collectDto() {
        SampleCollectDTO dto = new SampleCollectDTO();
        dto.setSampleBarcode(BARCODE);
        return dto;
    }

    private static SampleReceiveDTO receiveDto() {
        SampleReceiveDTO dto = new SampleReceiveDTO();
        dto.setSampleBarcode(BARCODE);
        dto.setInstrumentId(7L);
        return dto;
    }

    // ==================== 正向: 采集 ====================

    @Nested
    @DisplayName("collectSample: 已申请 → 已采集")
    class CollectSample {

        @Test
        @DisplayName("collectSample_statusApplied_transitionsToCollected")
        void collectSample_statusApplied_transitionsToCollected() {
            Sample s = sample(Sample.STATUS_APPLIED);
            givenSampleFound(s);

            SampleVO vo = service.collectSample(collectDto(), USER_ID);

            assertAll(
                    () -> assertEquals(Sample.STATUS_COLLECTED, s.getStatus()),
                    () -> assertNotNull(s.getCollectTime(), "采集时间必须落库"),
                    () -> assertEquals(Sample.STATUS_COLLECTED, vo.getStatus()),
                    () -> assertEquals("已采集", vo.getStatusText())
            );
            verify(service, times(1)).updateById(s);
        }

        @Test
        @DisplayName("collectSample_explicitCollectTime_usesProvidedTime")
        void collectSample_explicitCollectTime_usesProvidedTime() {
            Sample s = sample(Sample.STATUS_APPLIED);
            givenSampleFound(s);
            SampleCollectDTO dto = collectDto();
            dto.setCollectTime("2026-07-31T09:15:00");

            service.collectSample(dto, USER_ID);

            assertEquals("2026-07-31T09:15", s.getCollectTime().toString());
        }

        @Test
        @DisplayName("collectSample_illegalCollectTime_fallsBackToNow")
        void collectSample_illegalCollectTime_fallsBackToNow() {
            Sample s = sample(Sample.STATUS_APPLIED);
            givenSampleFound(s);
            SampleCollectDTO dto = collectDto();
            dto.setCollectTime("not-a-time");

            service.collectSample(dto, USER_ID);

            assertNotNull(s.getCollectTime(), "解析失败应回退当前时间而非置空");
            assertEquals(Sample.STATUS_COLLECTED, s.getStatus());
        }

        @Test
        @DisplayName("collectSample_alreadyCollected_throwsBusinessException")
        void collectSample_alreadyCollected_throwsBusinessException() {
            givenSampleFound(sample(Sample.STATUS_COLLECTED));

            BusinessException ex = assertThrows(BusinessException.class,
                    () -> service.collectSample(collectDto(), USER_ID));

            assertTrue(ex.getMessage().contains("不可重复采集"));
            verify(service, never()).updateById(any(Sample.class));
        }

        @Test
        @DisplayName("collectSample_alreadyReceived_throwsBusinessException")
        void collectSample_alreadyReceived_throwsBusinessException() {
            givenSampleFound(sample(Sample.STATUS_RECEIVED));

            assertThrows(BusinessException.class, () -> service.collectSample(collectDto(), USER_ID));
        }

        @Test
        @DisplayName("collectSample_barcodeNotFound_throwsBusinessException")
        void collectSample_barcodeNotFound_throwsBusinessException() {
            givenSampleFound(null);

            BusinessException ex = assertThrows(BusinessException.class,
                    () -> service.collectSample(collectDto(), USER_ID));

            assertTrue(ex.getMessage().contains("标本不存在"));
        }

        @Test
        @DisplayName("collectSample_noBarcodeAndNoId_throwsBusinessException")
        void collectSample_noBarcodeAndNoId_throwsBusinessException() {
            SampleCollectDTO dto = new SampleCollectDTO();

            BusinessException ex = assertThrows(BusinessException.class,
                    () -> service.collectSample(dto, USER_ID));

            assertTrue(ex.getMessage().contains("不能同时为空"));
        }
    }

    // ==================== 正向: 送检 ====================

    @Nested
    @DisplayName("deliverSamples: 已采集 → 已送检")
    class DeliverSamples {

        @Test
        @DisplayName("deliverSamples_statusCollected_transitionsToDelivered")
        void deliverSamples_statusCollected_transitionsToDelivered() {
            Sample s = sample(Sample.STATUS_COLLECTED);
            givenSampleFound(s);

            List<SampleVO> vos = service.deliverSamples(Collections.singletonList(BARCODE), USER_ID);

            assertAll(
                    () -> assertEquals(1, vos.size()),
                    () -> assertEquals(Sample.STATUS_DELIVERED, s.getStatus()),
                    () -> assertNotNull(s.getDeliverTime()),
                    () -> assertNotNull(s.getDeliverPerson(), "送检人姓名不可为空"),
                    () -> assertEquals("已送检", vos.get(0).getStatusText())
            );
            verify(service, times(1)).updateById(s);
        }

        @Test
        @DisplayName("deliverSamples_notCollectedYet_throwsBusinessException")
        void deliverSamples_notCollectedYet_throwsBusinessException() {
            givenSampleFound(sample(Sample.STATUS_APPLIED));

            BusinessException ex = assertThrows(BusinessException.class,
                    () -> service.deliverSamples(Collections.singletonList(BARCODE), USER_ID));

            assertTrue(ex.getMessage().contains("标本未采集"));
            verify(service, never()).updateById(any(Sample.class));
        }

        @Test
        @DisplayName("deliverSamples_alreadyDelivered_throwsBusinessException")
        void deliverSamples_alreadyDelivered_throwsBusinessException() {
            givenSampleFound(sample(Sample.STATUS_DELIVERED));

            BusinessException ex = assertThrows(BusinessException.class,
                    () -> service.deliverSamples(Collections.singletonList(BARCODE), USER_ID));

            assertTrue(ex.getMessage().contains("不可重复送检"));
        }

        @Test
        @DisplayName("deliverSamples_emptyBarcodeList_throwsBusinessException")
        void deliverSamples_emptyBarcodeList_throwsBusinessException() {
            assertThrows(BusinessException.class, () -> service.deliverSamples(Collections.emptyList(), USER_ID));
            assertThrows(BusinessException.class, () -> service.deliverSamples((List<String>) null, USER_ID));
        }
    }

    // ==================== 正向: 签收 ====================

    @Nested
    @DisplayName("signOffSamples: 已送检 → 已签收")
    class SignOffSamples {

        @Test
        @DisplayName("signOffSamples_statusDelivered_transitionsToSigned")
        void signOffSamples_statusDelivered_transitionsToSigned() {
            Sample s = sample(Sample.STATUS_DELIVERED);
            givenSampleFound(s);

            List<SampleVO> vos = service.signOffSamples(Collections.singletonList(BARCODE), USER_ID);

            assertAll(
                    () -> assertEquals(Sample.STATUS_SIGNED, s.getStatus()),
                    () -> assertNotNull(s.getSignOffTime()),
                    () -> assertNotNull(s.getSignOffPerson()),
                    () -> assertEquals("已签收", vos.get(0).getStatusText())
            );
        }

        @Test
        @DisplayName("signOffSamples_notDeliveredYet_throwsBusinessException")
        void signOffSamples_notDeliveredYet_throwsBusinessException() {
            givenSampleFound(sample(Sample.STATUS_COLLECTED));

            BusinessException ex = assertThrows(BusinessException.class,
                    () -> service.signOffSamples(Collections.singletonList(BARCODE), USER_ID));

            assertTrue(ex.getMessage().contains("标本未送检"));
        }

        @Test
        @DisplayName("signOffSamples_alreadySigned_throwsBusinessException")
        void signOffSamples_alreadySigned_throwsBusinessException() {
            givenSampleFound(sample(Sample.STATUS_SIGNED));

            BusinessException ex = assertThrows(BusinessException.class,
                    () -> service.signOffSamples(Collections.singletonList(BARCODE), USER_ID));

            assertTrue(ex.getMessage().contains("不可重复签收"));
        }
    }

    // ==================== 逆向: 取消送检 ====================

    @Nested
    @DisplayName("cancelDeliverSamples: 已送检 → 回退已采集")
    class CancelDeliver {

        @Test
        @DisplayName("cancelDeliverSamples_statusDelivered_revertsToCollected")
        void cancelDeliverSamples_statusDelivered_revertsToCollected() {
            Sample s = sample(Sample.STATUS_DELIVERED);
            s.setDeliverPerson("张三");
            s.setDeliverPersonId(9L);
            givenSampleFound(s);

            List<SampleVO> vos = service.cancelDeliverSamples(Collections.singletonList(BARCODE), USER_ID);

            assertAll(
                    () -> assertEquals(Sample.STATUS_COLLECTED, s.getStatus()),
                    () -> assertNull(s.getDeliverTime(), "送检时间必须清空"),
                    () -> assertNull(s.getDeliverPerson(), "送检人必须清空"),
                    () -> assertNull(s.getDeliverPersonId()),
                    () -> assertEquals("已采集", vos.get(0).getStatusText())
            );
            // 逆向操作用 LambdaUpdateWrapper 显式 set null, 不走 updateById
            verify(service, times(1)).update(ArgumentMatchers.<Wrapper<Sample>>any());
        }

        @Test
        @DisplayName("cancelDeliverSamples_notDelivered_throwsBusinessException")
        void cancelDeliverSamples_notDelivered_throwsBusinessException() {
            givenSampleFound(sample(Sample.STATUS_COLLECTED));

            BusinessException ex = assertThrows(BusinessException.class,
                    () -> service.cancelDeliverSamples(Collections.singletonList(BARCODE), USER_ID));

            assertTrue(ex.getMessage().contains("无需取消"));
            verify(service, never()).update(ArgumentMatchers.<Wrapper<Sample>>any());
        }

        @Test
        @DisplayName("cancelDeliverSamples_alreadySigned_throwsBusinessException")
        void cancelDeliverSamples_alreadySigned_throwsBusinessException() {
            givenSampleFound(sample(Sample.STATUS_SIGNED));

            assertThrows(BusinessException.class,
                    () -> service.cancelDeliverSamples(Collections.singletonList(BARCODE), USER_ID));
        }

        @Test
        @DisplayName("cancelDeliverSamples_alreadyReceived_throwsBusinessException")
        void cancelDeliverSamples_alreadyReceived_throwsBusinessException() {
            givenSampleFound(sample(Sample.STATUS_RECEIVED));

            assertThrows(BusinessException.class,
                    () -> service.cancelDeliverSamples(Collections.singletonList(BARCODE), USER_ID));
        }

        @Test
        @DisplayName("cancelDeliverSamples_emptyBarcodeList_throwsBusinessException")
        void cancelDeliverSamples_emptyBarcodeList_throwsBusinessException() {
            assertThrows(BusinessException.class,
                    () -> service.cancelDeliverSamples(Collections.emptyList(), USER_ID));
        }
    }

    // ==================== 逆向: 取消签收 ====================

    @Nested
    @DisplayName("cancelSignOffSamples: 已签收 → 回退已送检")
    class CancelSignOff {

        @Test
        @DisplayName("cancelSignOffSamples_statusSigned_revertsToDelivered")
        void cancelSignOffSamples_statusSigned_revertsToDelivered() {
            Sample s = sample(Sample.STATUS_SIGNED);
            s.setSignOffPerson("李四");
            s.setSignOffPersonId(9L);
            givenSampleFound(s);

            List<SampleVO> vos = service.cancelSignOffSamples(Collections.singletonList(BARCODE), USER_ID);

            assertAll(
                    () -> assertEquals(Sample.STATUS_DELIVERED, s.getStatus()),
                    () -> assertNull(s.getSignOffTime(), "签收时间必须清空"),
                    () -> assertNull(s.getSignOffPerson(), "签收人必须清空"),
                    () -> assertNull(s.getSignOffPersonId()),
                    () -> assertEquals("已送检", vos.get(0).getStatusText())
            );
            verify(service, times(1)).update(ArgumentMatchers.<Wrapper<Sample>>any());
        }

        @Test
        @DisplayName("cancelSignOffSamples_notSigned_throwsBusinessException")
        void cancelSignOffSamples_notSigned_throwsBusinessException() {
            givenSampleFound(sample(Sample.STATUS_DELIVERED));

            BusinessException ex = assertThrows(BusinessException.class,
                    () -> service.cancelSignOffSamples(Collections.singletonList(BARCODE), USER_ID));

            assertTrue(ex.getMessage().contains("无需取消签收"));
        }

        @Test
        @DisplayName("cancelSignOffSamples_alreadyReceived_throwsBusinessException")
        void cancelSignOffSamples_alreadyReceived_throwsBusinessException() {
            givenSampleFound(sample(Sample.STATUS_RECEIVED));

            assertThrows(BusinessException.class,
                    () -> service.cancelSignOffSamples(Collections.singletonList(BARCODE), USER_ID));
            verify(service, never()).update(ArgumentMatchers.<Wrapper<Sample>>any());
        }
    }

    // ==================== 正向: 核收 ====================

    @Nested
    @DisplayName("receiveSample: 已送检/已签收 → 已核收")
    class ReceiveSample {

        @Test
        @DisplayName("receiveSample_statusDelivered_transitionsToReceived")
        void receiveSample_statusDelivered_transitionsToReceived() {
            Sample s = sample(Sample.STATUS_DELIVERED);
            givenSampleFound(s);
            when(instrumentItemMapMapper.countEnabledById(7L)).thenReturn(1);
            when(instrumentItemMapMapper.selectMaxSampleNoByInstrumentId(7L)).thenReturn(null);

            SampleVO vo = service.receiveSample(receiveDto());

            assertAll(
                    () -> assertEquals(Sample.STATUS_RECEIVED, s.getStatus()),
                    () -> assertNotNull(s.getReceiveTime()),
                    () -> assertEquals(7L, s.getInstrumentId()),
                    () -> assertEquals("1", s.getSampleNo(), "首个标本号应从 1 开始"),
                    () -> assertEquals("已核收", vo.getStatusText())
            );
        }

        @Test
        @DisplayName("receiveSample_statusSigned_transitionsToReceived")
        void receiveSample_statusSigned_transitionsToReceived() {
            Sample s = sample(Sample.STATUS_SIGNED);
            givenSampleFound(s);
            when(instrumentItemMapMapper.countEnabledById(7L)).thenReturn(1);
            when(instrumentItemMapMapper.selectMaxSampleNoByInstrumentId(7L)).thenReturn("12");

            service.receiveSample(receiveDto());

            assertEquals(Sample.STATUS_RECEIVED, s.getStatus());
            assertEquals("13", s.getSampleNo(), "标本号应为当前最大值 + 1");
        }

        @Test
        @DisplayName("receiveSample_statusApplied_throwsBusinessException")
        void receiveSample_statusApplied_throwsBusinessException() {
            givenSampleFound(sample(Sample.STATUS_APPLIED));

            BusinessException ex = assertThrows(BusinessException.class,
                    () -> service.receiveSample(receiveDto()));

            assertTrue(ex.getMessage().contains("必须为已送检(2)或已签收(3)"));
            verify(instrumentItemMapMapper, never()).countEnabledById(anyLong());
        }

        @Test
        @DisplayName("receiveSample_alreadyReceived_throwsBusinessException")
        void receiveSample_alreadyReceived_throwsBusinessException() {
            givenSampleFound(sample(Sample.STATUS_RECEIVED));

            BusinessException ex = assertThrows(BusinessException.class,
                    () -> service.receiveSample(receiveDto()));

            assertTrue(ex.getMessage().contains("不可重复核收"));
        }

        @Test
        @DisplayName("receiveSample_missingPatientId_throwsBusinessException")
        void receiveSample_missingPatientId_throwsBusinessException() {
            Sample s = sample(Sample.STATUS_DELIVERED);
            s.setPatientId(null);
            givenSampleFound(s);

            BusinessException ex = assertThrows(BusinessException.class,
                    () -> service.receiveSample(receiveDto()));

            assertTrue(ex.getMessage().contains("缺少患者信息"));
        }

        @Test
        @DisplayName("receiveSample_disabledInstrument_throwsBusinessException")
        void receiveSample_disabledInstrument_throwsBusinessException() {
            givenSampleFound(sample(Sample.STATUS_DELIVERED));
            when(instrumentItemMapMapper.countEnabledById(7L)).thenReturn(0);

            BusinessException ex = assertThrows(BusinessException.class,
                    () -> service.receiveSample(receiveDto()));

            assertTrue(ex.getMessage().contains("仪器不存在或已停用"));
        }

        @Test
        @DisplayName("receiveSample_emptyBarcode_throwsBusinessException")
        void receiveSample_emptyBarcode_throwsBusinessException() {
            SampleReceiveDTO dto = receiveDto();
            dto.setSampleBarcode("");

            BusinessException ex = assertThrows(BusinessException.class, () -> service.receiveSample(dto));

            assertTrue(ex.getMessage().contains("标本条码不能为空"));
        }

        @Test
        @DisplayName("receiveSample_nullInstrumentId_throwsBusinessException")
        void receiveSample_nullInstrumentId_throwsBusinessException() {
            SampleReceiveDTO dto = receiveDto();
            dto.setInstrumentId(null);

            BusinessException ex = assertThrows(BusinessException.class, () -> service.receiveSample(dto));

            assertTrue(ex.getMessage().contains("必须选择仪器"));
        }

        @Test
        @DisplayName("receiveSample_barcodeNotFound_throwsBusinessException")
        void receiveSample_barcodeNotFound_throwsBusinessException() {
            givenSampleFound(null);

            BusinessException ex = assertThrows(BusinessException.class,
                    () -> service.receiveSample(receiveDto()));

            assertTrue(ex.getMessage().contains("标本不存在"));
        }
    }
}
