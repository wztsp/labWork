package com.lis.api.vo;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class ResultVO {

    private Long id;

    private Long sampleId;

    private Long testItemId;

    /** 项目代码 (英文名称/显示英文列使用) */
    private String testItemCode;

    /** 项目中文名 */
    private String testItemName;

    /** 项目英文名 (和 testItemCode 同义, 前端显示列名) */
    private String englishName;

    private String resultValue;

    private String resultText;

    private String unit;

    private String refRange;

    private String abnormalFlag;

    private String abnormalText;

    private Integer isCritical;

    private Long instrumentId;

    private String instrumentName;

    private Long operatorId;

    private String operatorName;

    private LocalDateTime testTime;

    private Integer auditStatus;

    private String auditStatusText;

    private Long auditorId;

    private LocalDateTime auditTime;

    private LocalDateTime createTime;

    /** 所属检验项目(检验项目) ID, 来自 lis_test_item.parent_id */
    private Long panelId;

    /** 所属检验项目(检验项目) 名称, 用于一个条码多个检验项目的分组显示 */
    private String panelName;

    /** 项目分类 (血常规/生化/免疫) */
    private String category;

    /** 解释信息/临床意义 */
    private String interpretation;

    /** 上次结果值 (历史结果对比, 来自该患者最近一次已发布报告的同项目结果) */
    private String lastResult;

    /** 上次结果异常标识 (H/L/N) */
    private String lastFlag;

    /** 上次检测时间 */
    private LocalDateTime lastTestTime;
}
