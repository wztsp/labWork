package com.lis.reagent.thirdparty.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 三方卫材对接配置。
 *
 * <p>每个对接方一条记录，接口地址、鉴权方式、业务路径、入站校验全部配置化，
 * 对方接口字段不同时只需调整字段映射表，无需改代码。</p>
 */
@Data
@TableName("lis_reagent_thirdparty_config")
public class ThirdPartyConfig implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 对接方编码，唯一 */
    private String partnerCode;
    /** 对接方名称 */
    private String partnerName;
    /** 0停用 1启用 */
    private Integer enabled;
    /** 对方接口根地址 */
    private String baseUrl;
    /** 鉴权方式：0无 1Token 2AK/SK签名 3Basic */
    private Integer authType;
    /** Token 鉴权时写入的请求头名称 */
    private String authHeader;
    private String authToken;
    private String appKey;
    private String appSecret;
    private String basicUsername;
    private String basicPassword;
    /** 请求超时（毫秒） */
    private Integer timeoutMs;
    /** 失败重试次数 */
    private Integer retryTimes;
    /** 采购订单下发路径 */
    private String orderPath;
    /** 商品主数据路径 */
    private String productPath;
    /** 供应商主数据路径 */
    private String supplierPath;
    /** 库存查询路径 */
    private String stockPath;
    /** 是否开放入站接口：0否 1是 */
    private Integer inboundEnabled;
    /** 入站校验 Token（对方调用时放在 X-Third-Token 请求头） */
    private String inboundToken;
    private String remark;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
