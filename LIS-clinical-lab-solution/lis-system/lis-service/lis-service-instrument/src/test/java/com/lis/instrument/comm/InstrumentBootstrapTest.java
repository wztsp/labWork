package com.lis.instrument.comm;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.lis.instrument.entity.Instrument;
import com.lis.instrument.mapper.InstrumentCommMapper;
import com.lis.instrument.testsupport.TableInfoTestSupport;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * {@link InstrumentBootstrap} 单元测试 —— B-6 自举触发时机与开关。
 * <p>
 * 关键设计点验证:
 * <ol>
 *     <li>实现 {@code ApplicationListener<ApplicationReadyEvent>} 而非
 *         {@code @PostConstruct} —— 保证数据源/Mapper 已完全就绪;</li>
 *     <li>{@code instrument.connection.auto-connect=false} 时<b>完全不查库</b>,
 *         离线开发环境不刷 WARN;</li>
 *     <li>只装载 {@code status=1} 的启用仪器。</li>
 * </ol>
 * <p>
 * 注: {@code onApplicationEvent} 不读取事件对象本身, 故用例直接传 {@code null},
 * 避免为构造 {@code ApplicationReadyEvent} 拉起整个 Spring Boot 上下文。
 */
@DisplayName("B-6 InstrumentBootstrap 仪器自举触发")
class InstrumentBootstrapTest {

    private InstrumentCommMapper instrumentMapper;
    private InstrumentConnectionManager connectionManager;
    private InstrumentBootstrap bootstrap;

    @BeforeAll
    static void registerTableInfo() {
        TableInfoTestSupport.ensureRegistered(Instrument.class);
    }

    @BeforeEach
    void setUp() {
        instrumentMapper = mock(InstrumentCommMapper.class);
        connectionManager = mock(InstrumentConnectionManager.class);
        bootstrap = new InstrumentBootstrap(instrumentMapper, connectionManager);
    }

    /** {@code @Value} 注入的私有字段, 单测用反射设置 (不引入 spring-test) */
    private void setAutoConnect(boolean value) throws Exception {
        Field f = InstrumentBootstrap.class.getDeclaredField("autoConnect");
        f.setAccessible(true);
        f.set(bootstrap, value);
    }

    private static Instrument instrument(long id) {
        Instrument i = new Instrument();
        i.setId(id);
        i.setInstrumentCode("INS" + id);
        i.setStatus(1);
        return i;
    }

    // ==================== 设计契约 ====================

    @Test
    @DisplayName("必须监听 ApplicationReadyEvent, 而非 @PostConstruct")
    void implementsApplicationReadyListener() {
        assertTrue(org.springframework.context.ApplicationListener.class
                        .isAssignableFrom(InstrumentBootstrap.class),
                "自举器应实现 ApplicationListener, 保证在容器完全就绪后才查库");
    }

    // ==================== 开关 ====================

    @Test
    @DisplayName("auto-connect=false: 完全不查库、不连接 (离线开发环境静默)")
    void autoConnectDisabled_noInteraction() throws Exception {
        setAutoConnect(false);

        bootstrap.onApplicationEvent(null);

        verifyNoInteractions(instrumentMapper);
        verifyNoInteractions(connectionManager);
    }

    @Test
    @DisplayName("auto-connect=true: 查库并把结果交给 autoConnectAll")
    void autoConnectEnabled_loadsAndConnects() throws Exception {
        setAutoConnect(true);
        List<Instrument> enabled = Arrays.asList(instrument(1L), instrument(2L));
        when(instrumentMapper.selectList(any())).thenReturn(enabled);
        when(connectionManager.autoConnectAll(any())).thenReturn(2);

        bootstrap.onApplicationEvent(null);

        verify(instrumentMapper, times(1)).selectList(any());
        ArgumentCaptor<List<Instrument>> captor = ArgumentCaptor.forClass(List.class);
        verify(connectionManager, times(1)).autoConnectAll(captor.capture());
        assertEquals(enabled, captor.getValue(), "应把查到的启用仪器原样交给管理器");
    }

    @Test
    @DisplayName("查询条件必须构造成功 (LambdaQueryWrapper 只筛 status=1 的启用仪器)")
    void queryWrapperIsBuilt() throws Exception {
        setAutoConnect(true);
        when(instrumentMapper.selectList(any())).thenReturn(Collections.emptyList());
        when(connectionManager.autoConnectAll(any())).thenReturn(0);

        bootstrap.onApplicationEvent(null);

        ArgumentCaptor<LambdaQueryWrapper> captor = ArgumentCaptor.forClass(LambdaQueryWrapper.class);
        verify(instrumentMapper).selectList(captor.capture());
        LambdaQueryWrapper<?> wrapper = captor.getValue();
        assertNotNull(wrapper, "应传入非空的查询条件");
        String sql = wrapper.getTargetSql();
        assertTrue(sql.contains("status"),
                "查询条件应按 status 过滤, 实际 SQL 片段: " + sql);
    }

    @Test
    @DisplayName("台账为空: 仍调用 autoConnectAll (由其内部返回 0), 不抛异常")
    void emptyLedger_stillDelegates() throws Exception {
        setAutoConnect(true);
        when(instrumentMapper.selectList(any())).thenReturn(Collections.emptyList());
        when(connectionManager.autoConnectAll(any())).thenReturn(0);

        bootstrap.onApplicationEvent(null);

        verify(connectionManager, times(1)).autoConnectAll(Collections.emptyList());
    }

    @Test
    @DisplayName("默认值: 未显式配置时 auto-connect 默认为 true (生产自举)")
    void defaultValueAnnotationIsTrue() throws Exception {
        Field f = InstrumentBootstrap.class.getDeclaredField("autoConnect");
        org.springframework.beans.factory.annotation.Value v =
                f.getAnnotation(org.springframework.beans.factory.annotation.Value.class);
        assertNotNull(v, "autoConnect 应由 @Value 注入");
        assertEquals("${instrument.connection.auto-connect:true}", v.value(),
                "缺省值应为 true, 由开发档 application.yml 显式关闭");
    }
}
