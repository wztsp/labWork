package com.lis.instrument.comm;

import com.lis.instrument.comm.serial.SerialConnector;
import com.lis.instrument.comm.tcp.TcpConnector;
import com.lis.instrument.entity.Instrument;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 仪器连接管理器
 * <p>
 * 维护所有仪器连接 (Map&lt;Long, Connection&gt;)，提供：
 * 1. 启动时自动连接 (实际场景从仪器台账表加载配置)
 * 2. 定时心跳检测
 * 3. 断线重连
 * 4. 离线告警 (发送 MQ 消息 instrument.offline.alert)
 * 5. 双通道热备: 支持主通道(TCP)+备通道(串口), 主通道断开时自动切换到备通道,
 *    并发 MQ 告警 (instrument.channel.failover)
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class InstrumentConnectionManager {

    /** 仪器ID -> 主通道连接 */
    private final Map<Long, Connection> connectionMap = new ConcurrentHashMap<>();
    /** 仪器ID -> 备通道连接 (双通道热备) */
    private final Map<Long, Connection> backupConnectionMap = new ConcurrentHashMap<>();
    /** 已发生通道切换的仪器ID集合, 避免重复告警 */
    private final Set<Long> failedOver = new HashSet<>();
    /** 已发送离线告警的仪器ID集合，避免重复告警 */
    private final Set<Long> offlineAlerted = new HashSet<>();
    /** 自举幂等锁: 保证 autoConnectAll 进程内仅完整执行一次 */
    private final AtomicBoolean bootstrapped = new AtomicBoolean(false);

    private final RabbitTemplate rabbitTemplate;

    @Value("${instrument.connection.connect-timeout:5000}")
    private int connectTimeout;
    @Value("${mq.exchange:lis.exchange}")
    private String exchange;
    @Value("${mq.instrument.offline-alert-routing-key:instrument.offline.alert}")
    private String offlineAlertRoutingKey;
    @Value("${mq.instrument.failover-routing-key:instrument.channel.failover}")
    private String failoverRoutingKey;

    /**
     * 启动时自动连接 (简化: 实际场景应从仪器台账表加载配置)
     */
    @PostConstruct
    public void init() {
        log.info("仪器连接管理器初始化... (生产环境应从仪器台账表加载配置并自动连接)");
    }

    /**
     * 批量自举连接 (B-6 启动不自举修复)。
     * <p>
     * <b>幂等</b>: 借助 {@link AtomicBoolean#compareAndSet(boolean, boolean)} 保证进程内仅完整执行一次;
     * 重复调用 (如测试二次触发) 直接返回 0 且不新建任何连接。
     * 单台仪器采用二级幂等 (已存在连接则跳过), 单台连接异常绝不阻断其余仪器。
     *
     * @param instruments 待连接的仪器列表 (通常来自台账中 status=1 的仪器)
     * @return 本次成功建立的连接数
     */
    public int autoConnectAll(List<Instrument> instruments) {
        if (instruments == null || instruments.isEmpty()) {
            return 0;
        }
        if (!bootstrapped.compareAndSet(false, true)) {
            return 0;
        }
        int connected = 0;
        for (Instrument instrument : instruments) {
            Long id = instrument.getId();
            // 二级幂等: 已存在活跃连接则跳过
            if (connectionMap.containsKey(id)) {
                continue;
            }
            try {
                boolean ok;
                String channelType = instrument.getChannelType();
                String connectType = instrument.getConnectType();
                if ("TCP_SERIAL".equals(channelType)) {
                    // 主通道 TCP + 备通道 串口
                    if (hasText(instrument.getIpAddress()) && instrument.getPort() != null
                            && hasText(instrument.getSerialPort()) && instrument.getBaudRate() != null) {
                        ok = connectDual(id, instrument.getInstrumentCode(),
                                instrument.getIpAddress(), instrument.getPort(),
                                instrument.getSerialPort(), instrument.getBaudRate());
                    } else {
                        log.warn("仪器[{}]双通道配置不完整, 跳过自举", id);
                        continue;
                    }
                } else if ("SERIAL".equals(connectType)) {
                    if (hasText(instrument.getSerialPort()) && instrument.getBaudRate() != null) {
                        ok = connectSerial(id, instrument.getInstrumentCode(),
                                instrument.getSerialPort(), instrument.getBaudRate());
                    } else {
                        log.warn("仪器[{}]串口配置不完整, 跳过自举", id);
                        continue;
                    }
                } else if (hasText(instrument.getIpAddress()) && instrument.getPort() != null) {
                    ok = connectTcp(id, instrument.getInstrumentCode(),
                            instrument.getIpAddress(), instrument.getPort());
                } else {
                    log.warn("仪器[{}]通信配置不完整 (缺 IP/端口/串口), 跳过自举", id);
                    continue;
                }
                if (ok) {
                    connected++;
                }
            } catch (Exception e) {
                // 单台失败绝不阻断启动
                log.error("仪器[{}]自动连接异常", id, e);
            }
        }
        return connected;
    }

    /** 简单的非空文本判断 (避免引入额外依赖) */
    private boolean hasText(String value) {
        return value != null && !value.isEmpty();
    }

    /**
     * 销毁时关闭所有连接
     */
    @PreDestroy
    public void destroy() {
        log.info("关闭所有仪器连接...");
        connectionMap.values().forEach(Connection::disconnect);
        backupConnectionMap.values().forEach(Connection::disconnect);
        connectionMap.clear();
        backupConnectionMap.clear();
    }

    /**
     * 添加并连接仪器 (TCP)
     */
    public boolean connectTcp(Long instrumentId, String instrumentCode,
                              String host, int port) {
        if (connectionMap.containsKey(instrumentId)) {
            log.warn("仪器已存在连接: {}, 先断开", instrumentId);
            disconnect(instrumentId);
        }
        TcpConnector conn = new TcpConnector(instrumentId, instrumentCode, host, port, connectTimeout);
        boolean ok = conn.connect();
        connectionMap.put(instrumentId, conn);
        if (ok) {
            offlineAlerted.remove(instrumentId);
        }
        return ok;
    }

    /**
     * 添加并连接仪器 (串口)
     */
    public boolean connectSerial(Long instrumentId, String instrumentCode,
                                 String portName, int baudRate) {
        if (connectionMap.containsKey(instrumentId)) {
            disconnect(instrumentId);
        }
        SerialConnector conn = new SerialConnector(instrumentId, instrumentCode, portName, baudRate);
        boolean ok = conn.connect();
        connectionMap.put(instrumentId, conn);
        if (ok) {
            offlineAlerted.remove(instrumentId);
        }
        return ok;
    }

    /**
     * 双通道热备连接: 同时连接主通道(TCP)和备通道(串口)
     * @return 主通道是否连接成功 (备通道失败仅告警, 不影响主流程)
     */
    public boolean connectDual(Long instrumentId, String instrumentCode,
                               String host, int tcpPort,
                               String portName, int baudRate) {
        // 主通道 TCP
        TcpConnector primary = new TcpConnector(instrumentId, instrumentCode, host, tcpPort, connectTimeout);
        boolean primaryOk = primary.connect();
        connectionMap.put(instrumentId, primary);

        // 备通道 串口
        boolean backupOk = false;
        try {
            SerialConnector backup = new SerialConnector(instrumentId, instrumentCode, portName, baudRate);
            backupOk = backup.connect();
            backupConnectionMap.put(instrumentId, backup);
        } catch (Exception e) {
            log.warn("仪器 [{}] 备通道(串口)连接异常: {}", instrumentId, e.getMessage());
        }

        if (primaryOk) {
            offlineAlerted.remove(instrumentId);
        }
        if (!backupOk) {
            log.warn("仪器 [{}] 备通道(串口)未就绪, 仅主通道(TCP)工作", instrumentId);
        } else {
            log.info("仪器 [{}] 双通道热备就绪: 主=TCP, 备=串口", instrumentId);
        }
        return primaryOk;
    }

    /**
     * 断开指定仪器 (含备通道)
     */
    public void disconnect(Long instrumentId) {
        Connection conn = connectionMap.remove(instrumentId);
        if (conn != null) {
            conn.disconnect();
        }
        Connection backup = backupConnectionMap.remove(instrumentId);
        if (backup != null) {
            backup.disconnect();
        }
        failedOver.remove(instrumentId);
    }

    /**
     * 获取活动连接 (优先主通道, 主通道不可用则返回备通道)
     */
    public Connection getConnection(Long instrumentId) {
        Connection primary = connectionMap.get(instrumentId);
        if (primary != null && primary.isAlive()) {
            return primary;
        }
        // 主通道不可用, 回退到备通道
        Connection backup = backupConnectionMap.get(instrumentId);
        if (backup != null && backup.isAlive()) {
            return backup;
        }
        return primary;
    }

    /**
     * 获取所有主连接
     */
    public Map<Long, Connection> getAllConnections() {
        return connectionMap;
    }

    /**
     * 心跳检测 (每 30 秒)
     */
    @Scheduled(fixedDelayString = "${instrument.connection.heartbeat-interval:30}000")
    public void heartbeatCheck() {
        if (connectionMap.isEmpty() && backupConnectionMap.isEmpty()) return;
        log.debug("心跳检测: {} 台仪器", connectionMap.size());
        connectionMap.forEach((id, conn) -> {
            try {
                if (!conn.heartbeat()) {
                    // 主通道心跳失败
                    Connection backup = backupConnectionMap.get(id);
                    if (backup != null && backup.isAlive()) {
                        // 主通道断开, 备通道可用 -> 切换到备通道并发告警
                        failoverToBackup(id, conn, backup);
                    } else {
                        // 无可用备通道, 尝试重连主通道
                        log.warn("仪器 [{}] 心跳失败, 尝试重连", id);
                        boolean reconnected = reconnect(id);
                        if (!reconnected) {
                            sendOfflineAlert(id, conn);
                        }
                    }
                } else {
                    offlineAlerted.remove(id);
                }
            } catch (Exception e) {
                log.error("仪器 [{}] 心跳异常", id, e);
                sendOfflineAlert(id, conn);
            }
        });
    }

    /**
     * 主通道故障, 切换到备通道并发 MQ 告警
     */
    private void failoverToBackup(Long instrumentId, Connection primary, Connection backup) {
        // 交换主备: 备通道提升为主通道
        connectionMap.put(instrumentId, backup);
        backupConnectionMap.remove(instrumentId);
        try {
            primary.disconnect();
        } catch (Exception e) {
            log.warn("断开故障主通道异常: {}", instrumentId, e.getMessage());
        }
        if (failedOver.contains(instrumentId)) {
            return;
        }
        Map<String, Object> payload = new HashMap<>();
        payload.put("instrumentId", instrumentId);
        payload.put("instrumentCode", backup.getInstrumentCode());
        payload.put("primaryEndpoint", primary.getEndpoint());
        payload.put("backupEndpoint", backup.getEndpoint());
        payload.put("failoverTime", LocalDateTime.now().toString());
        payload.put("message", "主通道故障, 已切换到备通道: " + backup.getEndpoint());
        try {
            rabbitTemplate.convertAndSend(exchange, failoverRoutingKey, payload);
            failedOver.add(instrumentId);
            log.warn("仪器 [{}] 主通道故障, 已切换到备通道 [{}], 告警已发送",
                    instrumentId, backup.getEndpoint());
        } catch (Exception e) {
            log.error("仪器 [{}] 通道切换告警发送失败", instrumentId, e);
        }
    }

    /**
     * 发送仪器离线告警
     */
    private void sendOfflineAlert(Long instrumentId, Connection conn) {
        if (offlineAlerted.contains(instrumentId)) {
            return;
        }
        Map<String, Object> payload = new HashMap<>();
        payload.put("instrumentId", instrumentId);
        payload.put("instrumentCode", conn == null ? null : conn.getInstrumentCode());
        payload.put("endpoint", conn == null ? null : conn.getEndpoint());
        payload.put("alertTime", LocalDateTime.now().toString());
        payload.put("message", "仪器离线: " + instrumentId);
        try {
            rabbitTemplate.convertAndSend(exchange, offlineAlertRoutingKey, payload);
            offlineAlerted.add(instrumentId);
            log.warn("仪器 [{}] 离线告警已发送", instrumentId);
        } catch (Exception e) {
            log.error("仪器 [{}] 离线告警发送失败", instrumentId, e);
        }
    }

    /**
     * 断线重连 (仅主通道)
     */
    public boolean reconnect(Long instrumentId) {
        Connection conn = connectionMap.get(instrumentId);
        if (conn == null) {
            log.warn("仪器 [{}] 无连接记录，无法重连", instrumentId);
            return false;
        }
        conn.disconnect();
        boolean ok = conn.connect();
        if (ok) {
            log.info("仪器 [{}] 重连成功", instrumentId);
            offlineAlerted.remove(instrumentId);
            failedOver.remove(instrumentId);
        } else {
            log.error("仪器 [{}] 重连失败", instrumentId);
        }
        return ok;
    }
}
