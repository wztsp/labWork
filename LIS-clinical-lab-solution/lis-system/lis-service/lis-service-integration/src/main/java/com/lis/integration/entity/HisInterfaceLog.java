package com.lis.integration.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * HIS 接口调用日志（出站：LIS 调 HIS 标准服务；入站：HIS 调 LIS 接收接口）。
 */
@Data
@TableName("lis_his_zl_log")
public class HisInterfaceLog implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 服务编号，如 S4009 / S4010 / S4020 */
    private String serviceNo;

    /** 报文流水号 head.action_no */
    private String actionNo;

    /** 1出站（LIS调HIS） 2入站（HIS调LIS） */
    private Integer direction;

    private String url;

    private String requestBody;

    private String responseBody;

    private Integer httpStatus;

    /** 0失败 1成功 */
    private Integer success;

    private String errorMsg;

    private Long costMs;

    /** 关联业务单号：申请号/条码/报告号等 */
    private String refNo;

    private LocalDateTime createTime;
}
