package com.lis.reagent.thirdparty.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 三方卫材字段映射。
 *
 * <p>{@code localField} 为本系统标准字段，{@code remoteField} 为对方接口字段，
 * 支持 {@code a.b} 形式的嵌套路径；出站按 local→remote 组包，入站按 remote→local 解析。</p>
 */
@Data
@TableName("lis_reagent_thirdparty_field_map")
public class ThirdPartyFieldMap implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long id;

    private String partnerCode;
    /** ORDER 采购订单 / PRODUCT 商品 / SUPPLIER 供应商 / STOCK 库存 / DELIVERY 到货 */
    private String bizType;
    /** 1出站 2入站 */
    private Integer direction;
    /** 本系统字段名 */
    private String localField;
    /** 对方字段名，支持嵌套路径 */
    private String remoteField;
    /** 对方字段缺省值 */
    private String defaultValue;
    private String remark;
    private LocalDateTime createTime;
}
