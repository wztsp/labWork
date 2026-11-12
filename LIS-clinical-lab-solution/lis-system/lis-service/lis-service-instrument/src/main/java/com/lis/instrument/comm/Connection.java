package com.lis.instrument.comm;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 仪器连接抽象
 */
@Data
public abstract class Connection {

    /** 仪器ID */
    protected Long instrumentId;

    /** 仪器编码 */
    protected String instrumentCode;

    /** 连接状态: 0=未连接 1=已连接 2=连接中 3=异常 */
    protected int status;

    /** 最后通讯时间 */
    protected LocalDateTime lastCommunicateTime;

    /** 连接配置 (串口名 / IP:port) */
    protected String endpoint;

    /** 连接类型: SERIAL / TCP */
    protected String connectType;

    public static final int STATUS_DISCONNECTED = 0;
    public static final int STATUS_CONNECTED = 1;
    public static final int STATUS_CONNECTING = 2;
    public static final int STATUS_ERROR = 3;

    /**
     * 建立连接
     */
    public abstract boolean connect();

    /**
     * 断开连接
     */
    public abstract void disconnect();

    /**
     * 发送数据
     */
    public abstract boolean send(String data);

    /**
     * 接收数据 (阻塞读一行)
     */
    public abstract String receive();

    /**
     * 是否存活
     */
    public abstract boolean isAlive();

    /**
     * 心跳检测
     */
    public abstract boolean heartbeat();
}
