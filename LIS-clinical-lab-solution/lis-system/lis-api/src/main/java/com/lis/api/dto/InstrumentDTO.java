package com.lis.api.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class InstrumentDTO {

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

    /** 通道类型: TCP(仅主通道) / TCP_SERIAL(主TCP+备串口双通道热备) */
    private String channelType;

    /** 序列号 */
    private String serialNo;

    /** 安装日期 */
    private LocalDate installDate;

    /** 保修到期 */
    private LocalDate warrantyExpiry;

    /** 使用科室 */
    private String department;

    /** 工作状态: 1正常 2维护中 3故障中 */
    private Integer workStatus;

    /** 最后在线时间 */
    private LocalDateTime lastOnlineTime;
}
