package com.lis.instrument.server;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lis.instrument.entity.Instrument;
import com.lis.instrument.entity.InstrumentCommLog;
import com.lis.instrument.mapper.InstrumentCommMapper;
import com.lis.instrument.matching.ResultMatchingService;
import com.lis.instrument.protocol.ProtocolParser;
import com.lis.instrument.protocol.ProtocolParserFactory;
import com.lis.instrument.service.CommLogService;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * 仪器数据接收 TCP 服务
 * <p>
 * 使用 Java ServerSocket 监听固定端口 (默认 9100), 每台仪器连接使用独立线程处理。
 * 接收到数据后: 记录通讯日志 → 调用协议解析器 → 调用 ResultMatchingService.matchAndSave。
 * <p>
 * 仪器识别: 按客户端 IP 匹配 lis_instrument.ip_address, 命中后使用其配置的 protocolType。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TcpServer {

    private final ProtocolParserFactory parserFactory;
    private final ResultMatchingService resultMatchingService;
    private final CommLogService commLogService;
    private final InstrumentCommMapper instrumentMapper;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Value("${tcp.server.enabled:true}")
    private boolean enabled;
    @Value("${tcp.server.port:9100}")
    private int port;

    private ServerSocket serverSocket;
    private ExecutorService acceptExecutor;
    private ExecutorService workerExecutor;
    private volatile boolean running = false;

    @PostConstruct
    public void start() {
        if (!enabled) {
            log.info("TCP 数据接收服务已禁用 (tcp.server.enabled=false)");
            return;
        }
        acceptExecutor = Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r, "tcp-server-accept");
            t.setDaemon(true);
            return t;
        });
        workerExecutor = Executors.newCachedThreadPool(r -> {
            Thread t = new Thread(r, "tcp-server-worker");
            t.setDaemon(true);
            return t;
        });
        running = true;
        acceptExecutor.submit(this::acceptLoop);
        log.info("TCP 数据接收服务已启动, 监听端口: {}", port);
    }

    @PreDestroy
    public void stop() {
        log.info("关闭 TCP 数据接收服务...");
        running = false;
        try {
            if (serverSocket != null && !serverSocket.isClosed()) {
                serverSocket.close();
            }
        } catch (Exception e) {
            log.warn("关闭 ServerSocket 异常", e);
        }
        if (acceptExecutor != null) {
            acceptExecutor.shutdownNow();
        }
        if (workerExecutor != null) {
            workerExecutor.shutdown();
            try {
                if (!workerExecutor.awaitTermination(5, TimeUnit.SECONDS)) {
                    workerExecutor.shutdownNow();
                }
            } catch (InterruptedException e) {
                workerExecutor.shutdownNow();
                Thread.currentThread().interrupt();
            }
        }
        log.info("TCP 数据接收服务已关闭");
    }

    /**
     * 接收连接循环
     */
    private void acceptLoop() {
        try {
            serverSocket = new ServerSocket(port);
            while (running && !serverSocket.isClosed()) {
                Socket socket = serverSocket.accept();
                workerExecutor.submit(() -> handleConnection(socket));
            }
        } catch (Exception e) {
            if (running) {
                log.error("TCP 接收服务异常退出", e);
            }
        }
    }

    /**
     * 处理单个仪器连接
     */
    private void handleConnection(Socket socket) {
        String remoteIp = ((InetSocketAddress) socket.getRemoteSocketAddress()).getAddress().getHostAddress();
        log.info("仪器连接接入: {}", remoteIp);

        // 按客户端 IP 匹配仪器
        Instrument instrument = findInstrumentByIp(remoteIp);
        Long instrumentId = instrument != null ? instrument.getId() : null;
        String protocolType = instrument != null && instrument.getProtocolType() != null
                ? instrument.getProtocolType() : "ASTM";

        StringBuilder buffer = new StringBuilder();
        try (Socket s = socket;
             BufferedReader reader = new BufferedReader(new InputStreamReader(s.getInputStream()));
             OutputStream out = s.getOutputStream()) {
            String line;
            while (running && (line = reader.readLine()) != null) {
                buffer.append(line).append("\n");
                // ASTM 消息以 L 记录结尾, 收到 L 视为一帧完整消息
                if (line.startsWith("L") || line.startsWith("l")) {
                    processFrame(instrumentId, protocolType, buffer.toString(), remoteIp);
                    buffer.setLength(0);
                }
            }
            // 连接断开时, 处理残留缓冲
            if (buffer.length() > 0) {
                processFrame(instrumentId, protocolType, buffer.toString(), remoteIp);
            }
        } catch (Exception e) {
            log.error("处理仪器连接异常: ip={}", remoteIp, e);
        } finally {
            log.info("仪器连接断开: ip={}", remoteIp);
        }
    }

    /**
     * 处理一帧完整报文: 记录通讯日志 → 解析 → 匹配保存
     */
    private void processFrame(Long instrumentId, String protocolType, String rawMessage, String remoteIp) {
        String parseStatus = "SUCCESS";
        String parseResult = null;
        try {
            ProtocolParser parser = parserFactory.getParser(protocolType);
            List<Map<String, String>> results = parser.parse(rawMessage);
            parseResult = objectMapper.writeValueAsString(results);
            if (instrumentId != null) {
                resultMatchingService.matchAndSave(instrumentId, protocolType, results);
            } else {
                log.warn("未识别仪器 (ip={}), 结果未匹配保存", remoteIp);
            }
        } catch (Exception e) {
            parseStatus = "FAIL";
            parseResult = "{\"error\":\"" + e.getMessage() + "\"}";
            log.error("报文解析失败: ip={}", remoteIp, e);
        } finally {
            saveCommLog(instrumentId, rawMessage, protocolType, parseStatus, parseResult);
        }
    }

    private void saveCommLog(Long instrumentId, String rawMessage, String protocolType,
                             String parseStatus, String parseResult) {
        try {
            InstrumentCommLog commLog = new InstrumentCommLog();
            commLog.setInstrumentId(instrumentId);
            commLog.setDirection("RECEIVE");
            commLog.setRawMessage(rawMessage);
            commLog.setProtocolType(protocolType);
            commLog.setParseStatus(parseStatus);
            commLog.setParseResult(parseResult);
            commLog.setMessageTime(LocalDateTime.now());
            commLogService.saveLog(commLog);
        } catch (Exception e) {
            log.error("保存通讯日志失败", e);
        }
    }

    /**
     * 按 IP 地址匹配仪器台账
     */
    private Instrument findInstrumentByIp(String ip) {
        if (ip == null || ip.isEmpty()) {
            return null;
        }
        try {
            return instrumentMapper.selectOne(new LambdaQueryWrapper<Instrument>()
                    .eq(Instrument::getIpAddress, ip)
                    .last("LIMIT 1"));
        } catch (Exception e) {
            log.warn("按 IP 查询仪器失败: ip={}", ip, e);
            return null;
        }
    }
}
