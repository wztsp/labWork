package com.lis.reagent.thirdparty.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 三方卫材商品映射：本系统试剂与对方商品编码的对应关系。
 */
@Data
@TableName("lis_reagent_thirdparty_product_map")
public class ThirdPartyProductMap implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long id;

    private String partnerCode;
    private Long reagentId;
    private String reagentCode;
    private String reagentName;
    /** 对方商品编码 */
    private String remoteCode;
    private String remoteName;
    private String remoteSpec;
    private String remoteUnit;
    private BigDecimal remotePrice;
    private LocalDateTime lastSyncTime;
    private String remark;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
