package com.lis.reagent.print.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 试剂管理打印模板（条码标签与各类单据）。
 *
 * <p>{@code templateJson} 保存画布与元素定义，坐标单位为 mm：
 * 前端设计器产出、打印渲染器消费，后端只做存取与合法性校验。</p>
 *
 * @author LIS Team
 */
@Data
@TableName("lis_print_template")
public class PrintTemplate {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 模板编码，全局唯一 */
    private String templateCode;

    /** 模板名称 */
    private String templateName;

    /** 业务类型：REAGENT_LABEL/PURCHASE_APPLY/PURCHASE_ORDER/INBOUND/REQUISITION/STOCK_CHECK/SCRAP_DISPOSAL */
    private String bizType;

    /** 纸张宽度（mm） */
    private BigDecimal paperWidth;

    /** 纸张高度（mm） */
    private BigDecimal paperHeight;

    /** 页边距（mm） */
    private BigDecimal margin;

    /** 画布与元素定义 JSON */
    private String templateJson;

    /** 0 否 1 是该业务类型的默认模板 */
    private Integer isDefault;

    /** 0 停用 1 启用 */
    private Integer status;

    private String remark;

    private String createBy;

    private LocalDateTime createTime;

    private String updateBy;

    private LocalDateTime updateTime;
}
