package com.lis.instrument.comm.tcp;

import com.lis.instrument.comm.Connection;
import lombok.extern.slf4j.Slf4j;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.time.LocalDateTime;

/**
 * TCP 连接器 (使用 Java Socket)
 */
@Slf4j
public class TcpConnector extends Connection {

    private final String host;
    private final int port;
    private final int connectTimeoutMs;
    private Socket socket;
    private OutputStream outputStream;
    private BufferedReader reader;

    public TcpConnector(Long instrumentId, String instrumentCode,
                        String host, int port, int connectTimeoutMs) {
        this.instrumentId = instrumentId;
        this.instrumentCode = instrumentCode;
        this.host = host;
        this.port = port;
        this.connectTimeoutMs = connectTimeoutMs;
        this.endpoint = host + ":" + port;
        this.connectType = "TCP";
    }

    @Override
    public boolean connect() {
        log.info("[TCP] 连接仪器: {} -> {}:{}", instrumentCode, host, port);
        try {
            socket = new Socket();
            socket.connect(new InetSocketAddress(host, port), connectTimeoutMs);
            socket.setKeepAlive(true);
            outputStream = socket.getOutputStream();
            reader = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            this.status = STATUS_CONNECTED;
            this.lastCommunicateTime = LocalDateTime.now();
            log.info("[TCP] 连接成功: {}", instrumentCode);
            return true;
        } catch (Exception e) {
            log.error("[TCP] 连接失败: {} -> {}:{}", instrumentCode, host, port, e);
            this.status = STATUS_ERROR;
            return false;
        }
    }

    @Override
    public void disconnect() {
        log.info("[TCP] 断开连接: {}", instrumentCode);
        try {
            if (reader != null) reader.close();
            if (outputStream != null) outputStream.close();
            if (socket != null) socket.close();
        } catch (Exception e) {
            log.warn("[TCP] 断开异常: {}", instrumentCode, e);
        } finally {
            socket = null;
            outputStream = null;
            reader = null;
            this.status = STATUS_DISCONNECTED;
        }
    }

    @Override
    public boolean send(String data) {
        if (outputStream == null) return false;
        try {
            outputStream.write(data.getBytes());
            outputStream.flush();
            this.lastCommunicateTime = LocalDateTime.now();
            log.debug("[TCP] send -> {}: {}", instrumentCode, data);
            return true;
        } catch (Exception e) {
            log.error("[TCP] 发送失败: {}", instrumentCode, e);
            this.status = STATUS_ERROR;
            return false;
        }
    }

    @Override
    public String receive() {
        if (reader == null) return null;
        try {
            String line = reader.readLine();
            if (line != null) {
                this.lastCommunicateTime = LocalDateTime.now();
                log.debug("[TCP] recv <- {}: {}", instrumentCode, line);
            }
            return line;
        } catch (Exception e) {
            log.error("[TCP] 接收失败: {}", instrumentCode, e);
            this.status = STATUS_ERROR;
            return null;
        }
    }

    @Override
    public boolean isAlive() {
        return socket != null && socket.isConnected() && !socket.isClosed();
    }

    @Override
    public boolean heartbeat() {
        if (!isAlive()) return false;
        // 简化: TCP keepAlive 已开启，此处只更新时间
        this.lastCommunicateTime = LocalDateTime.now();
        return true;
    }
}
