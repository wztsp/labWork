package com.lis.instrument.comm;

import com.lis.instrument.entity.Instrument;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.rabbit.core.RabbitTemplate;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

/**
 * {@link InstrumentConnectionManager#autoConnectAll(List)} 单元测试 —— B-6 (启动不自举) 修复验证。
 * <p>
 * 修复前 {@code @PostConstruct init()} 只打一行 {@code log.info}, 仪器从不自动连接。
 * 修复后连接装载下沉到本方法, 由 {@code InstrumentBootstrap} 在
 * {@code ApplicationReadyEvent} 时触发。
 * <p>
 * <b>本测试的核心是幂等断言</b>: {@code AtomicBoolean.compareAndSet(false,true)} 必须保证
 * 连续调用两次时, 实际连接动作只发生一次 —— 否则重复自举会把已建立的连接踢掉重连
 * (见 {@code connectTcp} 中 "仪器已存在连接, 先断开" 分支), 造成生产环境连接抖动。
 * <p>
 * 用 {@code spy} 桩化 {@code connectTcp/connectSerial/connectDual}, 避免真实建立 Socket/串口。
 */
@DisplayName("B-6 InstrumentConnectionManager.autoConnectAll 自举与幂等")
class InstrumentConnectionManagerAutoConnectTest {

    private InstrumentConnectionManager manager;

    @BeforeEach
    void setUp() {
        manager = spy(new InstrumentConnectionManager(mock(RabbitTemplate.class)));
        // 桩化真实建链方法, 单测不碰网络/串口
        doReturn(true).when(manager).connectTcp(anyLong(), anyString(), anyString(), anyInt());
        doReturn(true).when(manager).connectSerial(anyLong(), anyString(), anyString(), anyInt());
        doReturn(true).when(manager)
                .connectDual(anyLong(), anyString(), anyString(), anyInt(), anyString(), anyInt());
    }

    // ---------------- 测试数据 ----------------

    private static Instrument tcp(long id) {
        Instrument i = new Instrument();
        i.setId(id);
        i.setInstrumentCode("INS" + id);
        i.setConnectType("TCP");
        i.setIpAddress("192.168.1." + id);
        i.setPort(9000 + (int) id);
        i.setStatus(1);
        return i;
    }

    private static Instrument serial(long id) {
        Instrument i = new Instrument();
        i.setId(id);
        i.setInstrumentCode("INS" + id);
        i.setConnectType("SERIAL");
        i.setSerialPort("COM" + id);
        i.setBaudRate(9600);
        i.setStatus(1);
        return i;
    }

    private static Instrument dual(long id) {
        Instrument i = tcp(id);
        i.setChannelType("TCP_SERIAL");
        i.setSerialPort("COM" + id);
        i.setBaudRate(9600);
        return i;
    }

    // ==================== 幂等 (B-6 核心) ====================

    @Test
    @DisplayName("幂等: 连续调用两次, 实际连接动作只执行一次, 第二次返回 0")
    void autoConnectAll_calledTwice_onlyConnectsOnce() {
        List<Instrument> list = Arrays.asList(tcp(1L), tcp(2L));

        int first = manager.autoConnectAll(list);
        int second = manager.autoConnectAll(list);

        assertEquals(2, first, "首次自举应连上 2 台");
        assertEquals(0, second, "第二次自举必须被 AtomicBoolean 守卫拦下, 返回 0");
        verify(manager, times(2)).connectTcp(anyLong(), anyString(), anyString(), anyInt());
    }

    @Test
    @DisplayName("幂等: 第三次及以后调用同样不产生任何连接动作")
    void autoConnectAll_calledManyTimes_stillOnce() {
        List<Instrument> list = Collections.singletonList(tcp(1L));

        manager.autoConnectAll(list);
        for (int i = 0; i < 5; i++) {
            assertEquals(0, manager.autoConnectAll(list), "第 " + (i + 2) + " 次调用应返回 0");
        }
        verify(manager, times(1)).connectTcp(anyLong(), anyString(), anyString(), anyInt());
    }

    @Test
    @DisplayName("幂等: 第二次传入全新仪器列表也不会重复自举 (守卫是进程级)")
    void autoConnectAll_secondCallWithDifferentList_stillBlocked() {
        manager.autoConnectAll(Collections.singletonList(tcp(1L)));
        int second = manager.autoConnectAll(Collections.singletonList(tcp(99L)));

        assertEquals(0, second);
        verify(manager, never()).connectTcp(eq(99L), anyString(), anyString(), anyInt());
    }

    @Test
    @DisplayName("空列表/null 不消耗幂等令牌, 后续真实自举仍可执行")
    void autoConnectAll_emptyOrNull_doesNotConsumeGuard() {
        assertEquals(0, manager.autoConnectAll(null), "null 列表应直接返回 0");
        assertEquals(0, manager.autoConnectAll(Collections.emptyList()), "空列表应直接返回 0");

        // 令牌未被消耗, 真实自举照常进行
        assertEquals(1, manager.autoConnectAll(Collections.singletonList(tcp(1L))),
                "空调用不得提前消耗幂等令牌, 否则真实自举会被静默跳过");
        verify(manager, times(1)).connectTcp(anyLong(), anyString(), anyString(), anyInt());
    }

    // ==================== 通道选择 ====================

    @Test
    @DisplayName("TCP: 有 IP + 端口时走 connectTcp, 参数正确传递")
    void autoConnectAll_tcp_usesConnectTcp() {
        assertEquals(1, manager.autoConnectAll(Collections.singletonList(tcp(7L))));
        verify(manager, times(1)).connectTcp(7L, "INS7", "192.168.1.7", 9007);
        verify(manager, never()).connectSerial(anyLong(), anyString(), anyString(), anyInt());
        verify(manager, never())
                .connectDual(anyLong(), anyString(), anyString(), anyInt(), anyString(), anyInt());
    }

    @Test
    @DisplayName("SERIAL: connectType=SERIAL 且串口配置齐全时走 connectSerial")
    void autoConnectAll_serial_usesConnectSerial() {
        assertEquals(1, manager.autoConnectAll(Collections.singletonList(serial(3L))));
        verify(manager, times(1)).connectSerial(3L, "INS3", "COM3", 9600);
        verify(manager, never()).connectTcp(anyLong(), anyString(), anyString(), anyInt());
    }

    @Test
    @DisplayName("TCP_SERIAL: 双通道配置齐全时走 connectDual (优先级高于 connectType)")
    void autoConnectAll_dual_usesConnectDual() {
        assertEquals(1, manager.autoConnectAll(Collections.singletonList(dual(5L))));
        verify(manager, times(1)).connectDual(5L, "INS5", "192.168.1.5", 9005, "COM5", 9600);
        verify(manager, never()).connectTcp(anyLong(), anyString(), anyString(), anyInt());
        verify(manager, never()).connectSerial(anyLong(), anyString(), anyString(), anyInt());
    }

    // ==================== 配置不完整 → 跳过而非抛异常 ====================

    @Test
    @DisplayName("TCP_SERIAL 但缺串口: 跳过该台, 不降级为单 TCP, 不抛异常")
    void autoConnectAll_dualMissingSerial_skipped() {
        Instrument i = dual(5L);
        i.setSerialPort(null);

        assertEquals(0, manager.autoConnectAll(Collections.singletonList(i)));
        verify(manager, never())
                .connectDual(anyLong(), anyString(), anyString(), anyInt(), anyString(), anyInt());
        verify(manager, never()).connectTcp(anyLong(), anyString(), anyString(), anyInt());
    }

    @Test
    @DisplayName("SERIAL 但缺波特率: 跳过该台")
    void autoConnectAll_serialMissingBaudRate_skipped() {
        Instrument i = serial(3L);
        i.setBaudRate(null);

        assertEquals(0, manager.autoConnectAll(Collections.singletonList(i)));
        verify(manager, never()).connectSerial(anyLong(), anyString(), anyString(), anyInt());
    }

    @Test
    @DisplayName("无 IP / 无端口 / 无串口: 跳过该台且不抛异常")
    void autoConnectAll_noCommConfig_skipped() {
        Instrument i = new Instrument();
        i.setId(9L);
        i.setInstrumentCode("INS9");
        i.setStatus(1);

        assertEquals(0, assertDoesNotThrow(() -> manager.autoConnectAll(Collections.singletonList(i))));
        verify(manager, never()).connectTcp(anyLong(), anyString(), anyString(), anyInt());
    }

    @Test
    @DisplayName("有 IP 但缺端口: 跳过该台")
    void autoConnectAll_ipWithoutPort_skipped() {
        Instrument i = tcp(4L);
        i.setPort(null);

        assertEquals(0, manager.autoConnectAll(Collections.singletonList(i)));
        verify(manager, never()).connectTcp(anyLong(), anyString(), anyString(), anyInt());
    }

    // ==================== 容错: 单台失败不阻断其余 ====================

    @Test
    @DisplayName("单台连接抛异常: 捕获后继续处理其余仪器, 启动不被阻断")
    void autoConnectAll_oneThrows_othersContinue() {
        doThrow(new RuntimeException("socket refused"))
                .when(manager).connectTcp(eq(1L), anyString(), anyString(), anyInt());

        int ok = manager.autoConnectAll(Arrays.asList(tcp(1L), tcp(2L), tcp(3L)));

        assertEquals(2, ok, "异常的那台不计数, 其余 2 台仍应连上");
        verify(manager, times(1)).connectTcp(eq(2L), anyString(), anyString(), anyInt());
        verify(manager, times(1)).connectTcp(eq(3L), anyString(), anyString(), anyInt());
    }

    @Test
    @DisplayName("连接返回 false (仪器不可达) 时不计入成功数, 也不抛异常")
    void autoConnectAll_connectReturnsFalse_notCounted() {
        doReturn(false).when(manager).connectTcp(eq(2L), anyString(), anyString(), anyInt());

        int ok = manager.autoConnectAll(Arrays.asList(tcp(1L), tcp(2L)));

        assertEquals(1, ok, "仅 1 台连接成功");
    }

    @Test
    @DisplayName("混合列表: TCP / SERIAL / 双通道 / 配置不全 各走各的分支")
    void autoConnectAll_mixedList() {
        Instrument broken = new Instrument();
        broken.setId(99L);
        broken.setInstrumentCode("INS99");

        List<Instrument> list = new ArrayList<>(Arrays.asList(tcp(1L), serial(2L), dual(3L), broken));
        int ok = manager.autoConnectAll(list);

        assertEquals(3, ok, "4 台中 3 台配置完整");
        verify(manager, times(1)).connectTcp(eq(1L), anyString(), anyString(), anyInt());
        verify(manager, times(1)).connectSerial(eq(2L), anyString(), anyString(), anyInt());
        verify(manager, times(1))
                .connectDual(eq(3L), anyString(), anyString(), anyInt(), anyString(), anyInt());
    }
}
