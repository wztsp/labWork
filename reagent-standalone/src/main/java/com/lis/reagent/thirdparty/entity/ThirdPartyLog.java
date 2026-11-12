package com.lis.reagent.thirdparty.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 三方卫材接口调用日志（出站与入站统一留痕）。
 */
@Data
@TableName("lis_reagent_thirdparty_log")
public class ThirdPartyLog implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long id;

    private String partnerCode;
    /** ORDER / PRODUCT / SUPPLIER / STOCK / DELIVERY */
    private String bizType;
    /** 1出站 2入站 */
    private Integer direction;
    private String method;
    private String url;
    private String requestBody;
    private String responseBody;
    private Integer httpStatus;
    /** 0失败 1成功 */
    private Integer success;
    /** 关联业务单号 */
    private String bizNo;
    private String errorMsg;
    private Long costMs;
    private String operatorName;
    private LocalDateTime createTime;
}
