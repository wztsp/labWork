package com.lis.api.vo;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class InstrumentVO {

    private Long id;

    private String instrumentCode;

    private String instrumentName;

    private String brand;

    private String model;

    private String protocolType;

    private String connectType;

    private String serialPort;

    private Integer baudRate;

    private String ipAddress;

    private Integer port;

    private Integer status;

    private LocalDateTime lastOnlineTime;

    private LocalDateTime createTime;
}
