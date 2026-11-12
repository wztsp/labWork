package com.lis.instrument.comm.serial;

import com.lis.instrument.comm.Connection;
import jssc.SerialPort;
import jssc.SerialPortEvent;
import jssc.SerialPortEventListener;
import jssc.SerialPortException;
import lombok.extern.slf4j.Slf4j;

import java.time.LocalDateTime;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

/**
 * 串口连接器 (基于 jSSC 真实实现)
 * <p>
 * 打开串口并设置波特率/数据位/停止位/校验位, 通过事件监听线程异步读取数据,
 * 缓存到阻塞队列供 {@link #receive()} 消费。
 */
@Slf4j
public class SerialConnector extends Connection {

    private final String portName;
    private final int baudRate;
    /** 数据位: 5/6/7/8 */
    private final int dataBits;
    /** 停止位: 1/2/3 (对应 jssc: STOPBITS_1 / STOPBITS_2 / STOPBITS_1_5) */
    private final int stopBits;
    /** 校验位: 0无 1奇 2偶 (对应 jssc: PARITY_NONE / ODD / EVEN) */
    private final int parity;

    private SerialPort serialPort;
    /** 接收数据缓冲队列 */
    private final LinkedBlockingQueue<String> receiveQueue = new LinkedBlockingQueue<>(1024);
    /** 接收缓冲 (跨事件拼接不完整行) */
    private final StringBuilder lineBuffer = new StringBuilder();

    public SerialConnector(Long instrumentId, String instrumentCode, String portName, int baudRate) {
        this(instrumentId, instrumentCode, portName, baudRate,
                SerialPort.DATABITS_8, SerialPort.STOPBITS_1, SerialPort.PARITY_NONE);
    }

    public SerialConnector(Long instrumentId, String instrumentCode, String portName, int baudRate,
                           int dataBits, int stopBits, int parity) {
        this.instrumentId = instrumentId;
        this.instrumentCode = instrumentCode;
        this.portName = portName;
        this.baudRate = baudRate;
        this.dataBits = dataBits;
        this.stopBits = stopBits;
        this.parity = parity;
        this.endpoint = portName + "@" + baudRate;
        this.connectType = "SERIAL";
    }

    @Override
    public boolean connect() {
        log.info("[Serial] 打开串口: instrument={}, port={}, baud={}/{}/{}/{}",
                instrumentCode, portName, baudRate, dataBits, stopBits, parity);
        try {
            serialPort = new SerialPort(portName);
            serialPort.openPort();
            serialPort.setParams(baudRate, dataBits, stopBits, parity);
            // 设置掩码: 接收到数据时触发事件
            int mask = SerialPort.MASK_RXCHAR;
            serialPort.setEventsMask(mask);
            serialPort.addEventListener(new SerialPortEventListener() {
                @Override
                public void serialEvent(SerialPortEvent event) {
                    if (event.isRXCHAR() && event.getEventValue() > 0) {
                        try {
                            byte[] data = serialPort.readBytes(event.getEventValue());
                            if (data != null && data.length > 0) {
                                handleReceivedData(data);
                            }
                        } catch (SerialPortException e) {
                            log.error("[Serial] 读取串口数据异常: {}", instrumentCode, e);
                        }
                    }
                }
            });
            this.status = STATUS_CONNECTED;
            this.lastCommunicateTime = LocalDateTime.now();
            log.info("[Serial] 串口连接成功: {}", instrumentCode);
            return true;
        } catch (SerialPortException e) {
            log.error("[Serial] 串口连接失败: {} -> {}", instrumentCode, portName, e);
            this.status = STATUS_ERROR;
            return false;
        }
    }

    /**
     * 处理接收到的字节, 按行 (\n / \r) 切分后入队
     */
    private void handleReceivedData(byte[] data) {
        String text = new String(data);
        lineBuffer.append(text);
        int idx;
        while ((idx = indexOfLineEnd(lineBuffer)) >= 0) {
            int lineLen = idx;
            // 跳过换行符本身
            int nextStart = idx + 1;
            if (lineLen > 0) {
                String line = lineBuffer.substring(0, lineLen).trim();
                if (!line.isEmpty()) {
                    receiveQueue.offer(line);
                }
            }
            lineBuffer.delete(0, nextStart);
        }
        this.lastCommunicateTime = LocalDateTime.now();
    }

    private int indexOfLineEnd(StringBuilder sb) {
        for (int i = 0; i < sb.length(); i++) {
            char c = sb.charAt(i);
            if (c == '\n' || c == '\r') {
                return i;
            }
        }
        return -1;
    }

    @Override
    public void disconnect() {
        log.info("[Serial] 关闭串口: instrument={}", instrumentCode);
        if (serialPort != null) {
            try {
                if (serialPort.isOpened()) {
                    serialPort.closePort();
                }
            } catch (SerialPortException e) {
                log.warn("[Serial] 关闭串口异常: {}", instrumentCode, e);
            } finally {
                serialPort = null;
            }
        }
        receiveQueue.clear();
        lineBuffer.setLength(0);
        this.status = STATUS_DISCONNECTED;
    }

    @Override
    public boolean send(String data) {
        if (serialPort == null || !serialPort.isOpened()) {
            return false;
        }
        try {
            serialPort.writeBytes(data.getBytes());
            this.lastCommunicateTime = LocalDateTime.now();
            log.debug("[Serial] send -> {}: {}", instrumentCode, data);
            return true;
        } catch (SerialPortException e) {
            log.error("[Serial] 发送失败: {}", instrumentCode, e);
            this.status = STATUS_ERROR;
            return false;
        }
    }

    @Override
    public String receive() {
        if (serialPort == null) {
            return null;
        }
        try {
            // 阻塞等待一行 (最多 1 秒)
            return receiveQueue.poll(1, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return null;
        }
    }

    @Override
    public boolean isAlive() {
        return serialPort != null && serialPort.isOpened();
    }

    @Override
    public boolean heartbeat() {
        if (!isAlive()) {
            return false;
        }
        this.lastCommunicateTime = LocalDateTime.now();
        return true;
    }
}
