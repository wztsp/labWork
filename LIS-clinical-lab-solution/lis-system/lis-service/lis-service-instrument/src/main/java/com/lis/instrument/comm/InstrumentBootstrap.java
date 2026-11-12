package com.lis.instrument.comm;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.lis.instrument.entity.Instrument;
import com.lis.instrument.mapper.InstrumentCommMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 仪器自举器 (B-6 启动不自举修复)。
 * <p>
 * 监听 {@link ApplicationReadyEvent}: 容器<b>完全就绪</b>后, 从仪器台账加载"启用中"的仪器,
 * 交 {@link InstrumentConnectionManager#autoConnectAll(List)} 批量建立连接。
 * 之所以不用 {@code @PostConstruct} 而用 {@code ApplicationReadyEvent},
 * 是因为 {@code @PostConstruct} 时数据源 / Mapper 就绪次序不可靠, 而本监听在上下文刷新完成后才触发。
 * <p>
 * 连接的实际建立逻辑下沉到 {@link InstrumentConnectionManager}, 本类只负责"查哪些、要不要连",
 * 从而 {@link InstrumentConnectionManager} 仍可纯 Mockito 单测 (见 B-6 测试建议)。
 */
@Slf4j
@Component
public class InstrumentBootstrap implements ApplicationListener<ApplicationReadyEvent> {

    private final InstrumentCommMapper instrumentMapper;
    private final InstrumentConnectionManager connectionManager;

    /** 自举总开关, 开发/离线环境可置 false 避免启动刷 WARN */
    @Value("${instrument.connection.auto-connect:true}")
    private boolean autoConnect;

    public InstrumentBootstrap(InstrumentCommMapper instrumentMapper,
                               InstrumentConnectionManager connectionManager) {
        this.instrumentMapper = instrumentMapper;
        this.connectionManager = connectionManager;
    }

    @Override
    public void onApplicationEvent(ApplicationReadyEvent event) {
        if (!autoConnect) {
            log.info("仪器自举已关闭 (instrument.connection.auto-connect=false), 跳过自动连接");
            return;
        }
        // 仅装载启用中 (status=1) 的仪器
        List<Instrument> enabled = instrumentMapper.selectList(
                new LambdaQueryWrapper<Instrument>().eq(Instrument::getStatus, 1));
        int ok = connectionManager.autoConnectAll(enabled);
        log.info("仪器自举完成: 成功 {}/{}", ok, enabled.size());
    }
}
