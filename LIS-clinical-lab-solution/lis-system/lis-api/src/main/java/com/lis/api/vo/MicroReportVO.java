package com.lis.api.vo;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 微生物报告视图对象
 */
@Data
public class MicroReportVO {

    private Long id;

    private String reportNo;

    private Long sampleId;

    /** 标本条码 (采集工作站条码) */
    private String sampleBarcode;

    /** 标本号 */
    private String sampleNo;

    /** 当前报告对应的培养条码 (每条报告唯一对应一次核收的培养基条码) */
    private String plateBarcode;

    /** 培养条码 (培养基条码, 同标本可能多个, 顿号拼接 — 用于列表展示所有培养条码) */
    private String plateBarcodes;

    /** 申请项目 (检验项目名, 顿号拼接) */
    private String applyItems;

    /** 采集时间 */
    private LocalDateTime collectTime;

    /** 采集人 */
    private String collectorName;

    /** 核收时间 */
    private LocalDateTime receiveTime;

    /** 申请科室 */
    private String department;

    private Long patientId;

    private String patientName;

    private String specimenType;

    private String cultureSite;

    private Integer reportStatus;

    private String reportStatusText;

    private Long auditorId;

    private LocalDateTime auditTime;

    private LocalDateTime publishTime;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;

    // ============ 革兰氏染色 ============
    /** 革兰氏染色结果 */
    private String gramStainResult;

    /** 革兰氏染色备注 */
    private String gramStainRemark;

    /** 革兰氏染色时间 */
    private LocalDateTime gramStainTime;

    /** 革兰氏染色操作人 */
    private String gramStainOperator;

    // ============ 阴性报告 ============
    /** 是否阴性报告: 0=否 1=是 */
    private Integer isNegative;

    /** 阴性报告备注 */
    private String negativeRemark;

    /** 阴性报告操作人 */
    private String negativeOperator;

    /** 阴性报告时间 */
    private LocalDateTime negativeTime;

    // ============ 血培养专用 ============
    private String bottleNo;
    private String bottleType;
    private String volume;
    private LocalDateTime inoculateTime;
    private LocalDateTime alarmTime;
    private java.math.BigDecimal ttpHours;
    private Long bloodCultureId;
    private String bloodCultureResult;
    /** 转种记录列表 */
    private List<SubcultureVO> subcultures;

    /** 细菌鉴定列表 */
    private List<OrganismVO> organisms;

    /**
     * 细菌鉴定视图对象
     */
    @Data
    public static class OrganismVO {
        private Long id;
        private Long microReportId;
        private String organismName;
        private String organismCode;
        private String quantity;
        private String colonsMethod;
        private String remark;
        private LocalDateTime createTime;
        /** 该细菌对应的药敏结果列表 */
        private List<SensitivityVO> sensitivities;
    }

    /**
     * 药敏结果视图对象
     */
    @Data
    public static class SensitivityVO {
        private Long id;
        private Long microReportId;
        private Long organismId;
        private String antibioticName;
        private String antibioticCode;
        private String micValue;
        private String result;
        private String breakpoint;
        private String method;
        private String remark;
        private Integer zoneSize;
        private LocalDateTime createTime;
    }

    /**
     * 转种记录视图对象
     */
    @Data
    public static class SubcultureVO {
        private Long id;
        private Long microReportId;
        private LocalDateTime subcultureTime;
        private String medium;
        private String incubatorNo;
        private String result;
        private String operator;
        private String remark;
        private LocalDateTime createTime;
    }
}
