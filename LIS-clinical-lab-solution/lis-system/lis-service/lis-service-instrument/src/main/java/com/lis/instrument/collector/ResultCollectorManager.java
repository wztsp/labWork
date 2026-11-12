package com.lis.instrument.collector;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.lis.instrument.entity.Instrument;
import com.lis.instrument.mapper.InstrumentCommMapper;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

/**
 * 结果采集调度管理器
 * <p>
 * 服务启动后, 扫描台账中"启用中"且连接方式为 {@code FILE}/{@code DB} 的仪器,
 * 按各自 {@code pollInterval} 周期调度对应采集器执行任务。
 * 单个仪器异常不影响其它仪器采集。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ResultCollectorManager {

    /** 支持采集的连接方式 */
    private static final Set<String> COLLECT_CONNECT_TYPES = Set.of("FILE", "DB");

    private final InstrumentCommMapper instrumentMapper;
    private final List<ResultCollector> collectors;

    private ScheduledExecutorService scheduler;
    private final Map<Long, ScheduledFuture<?>> tasks = new HashMap<>();

    @PostConstruct
    public void init() {
        scheduler = Executors.newScheduledThreadPool(4, r -> {
            Thread t = new Thread(r, "result-collector");
            t.setDaemon(true);
            return t;
        });
        scheduleAll();
    }

    @PreDestroy
    public void destroy() {
        tasks.forEach((id, f) -> f.cancel(true));
        tasks.clear();
        if (scheduler != null) {
            scheduler.shutdownNow();
        }
    }

    private void scheduleAll() {
        List<Instrument> instruments = loadCollectableInstruments();
        if (instruments.isEmpty()) {
            log.info("无可采集(文件/数据库)仪器, 跳过结果采集调度");
            return;
        }
        for (Instrument inst : instruments) {
            scheduleInstrument(inst);
        }
        log.info("结果采集调度完成, 共调度 {} 台仪器", instruments.size());
    }

    private List<Instrument> loadCollectableInstruments() {
        try {
            return instrumentMapper.selectList(new LambdaQueryWrapper<Instrument>()
                    .in(Instrument::getConnectType, COLLECT_CONNECT_TYPES)
                    .eq(Instrument::getStatus, 1));
        } catch (Exception e) {
            log.warn("查询可采集仪器失败: {}", e.getMessage());
            return List.of();
        }
    }

    private void scheduleInstrument(Instrument inst) {
        ResultCollector collector = findCollector(inst.getConnectType());
        if (collector == null) {
            log.warn("仪器 [{}] 连接方式 [{}] 无对应采集器, 跳过", inst.getId(), inst.getConnectType());
            return;
        }
        long interval = intervalSeconds(inst);
        ScheduledFuture<?> future = scheduler.scheduleAtFixedRate(
                () -> safeCollect(inst, collector),
                interval, interval, TimeUnit.SECONDS);
        tasks.put(inst.getId(), future);
        log.info("已调度仪器 [{}] 结果采集, connectType={}, 间隔={}s",
                inst.getId(), inst.getConnectType(), interval);
    }

    private void safeCollect(Instrument inst, ResultCollector collector) {
        try {
            collector.collect(inst);
        } catch (Exception e) {
            log.error("仪器 [{}] 采集任务异常", inst.getId(), e);
        }
    }

    private ResultCollector findCollector(String connectType) {
        if (connectType == null) {
            return null;
        }
        for (ResultCollector c : collectors) {
            if (c.getConnectType() != null && c.getConnectType().equalsIgnoreCase(connectType)) {
                return c;
            }
        }
        return null;
    }

    private long intervalSeconds(Instrument inst) {
        Integer interval = inst.getPollInterval();
        if (interval == null || interval <= 0) {
            return 30;
        }
        return Math.min(interval, 3600);
    }
}