package com.lis.api.vo;

import lombok.Data;

import java.util.List;
import java.util.Map;

/**
 * 微生物流行病学统计视图对象
 */
@Data
public class MicroEpiStatVO {

    /** 统计时间范围开始 */
    private String beginDate;

    /** 统计时间范围结束 */
    private String endDate;

    /** 报告总数 */
    private int totalReportCount;

    /** 细菌鉴定总数 */
    private int totalOrganismCount;

    /** 药敏结果总数 */
    private int totalSensitivityCount;

    /** 预警总数 */
    private int totalAlertCount;

    /** 细菌分布统计 (菌名 -> 数量) */
    private List<ItemCount> organismDistribution;

    /** 标本类型分布 (标本类型 -> 数量) */
    private List<ItemCount> specimenTypeDistribution;

    /** 培养部位分布 (前10) */
    private List<ItemCount> cultureSiteDistribution;

    /** 抗生素耐药率统计 (抗生素名称 -> 耐药率%) */
    private List<ResistanceRate> resistanceRates;

    /** 月度检出趋势 (月份 -> 阳性检出数) */
    private List<ItemCount> monthlyTrend;

    /** 特殊耐药预警统计 (预警类型 -> 数量) */
    private List<ItemCount> alertTypeDistribution;

    /** 药敏结果分布 (S/I/R 数量) */
    private Map<String, Integer> resultDistribution;

    /**
     * 通用计数项
     */
    @Data
    public static class ItemCount {
        private String name;
        private int count;

        public ItemCount() {
        }

        public ItemCount(String name, int count) {
            this.name = name;
            this.count = count;
        }
    }

    /**
     * 耐药率统计项
     */
    @Data
    public static class ResistanceRate {
        private String antibioticName;
        private int totalCount;
        private int resistantCount;
        private int susceptibleCount;
        private int intermediateCount;
        /** 耐药率 % */
        private double resistanceRate;

        public ResistanceRate() {
        }

        public ResistanceRate(String antibioticName) {
            this.antibioticName = antibioticName;
        }
    }
}
