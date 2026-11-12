package com.lis.api.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 试管分组VO (后端根据申请项目自动计算合管结果)
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TubeGroupVO {

    /** 分组序号 (从1开始) */
    private Integer groupIndex;

    /** 试管ID (可能为null, 表示未绑定试管) */
    private Long tubeId;

    /** 试管名称 */
    private String tubeName;

    /** 试管颜色 */
    private String tubeColor;

    /** 标本类型 */
    private String sampleType;

    /** 容器类型 (试管名称, 用于创建标本时填充containerType) */
    private String containerType;

    /** 该管包含的检验项目列表 */
    private List<TestItemInfo> testItems;

    /** 项目数 */
    private Integer itemCount;

    /**
     * 检验项目简要信息
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TestItemInfo {
        /** 检验项目ID */
        private Long testItemId;
        /** 检验项目编码 */
        private String itemCode;
        /** 检验项目名称 */
        private String itemName;
        /** 标本类型 */
        private String specimenType;
        /** 试管ID */
        private Long tubeId;
        /** 申请ID (跨申请合管时, 标识该项目所属的申请) */
        private Long orderId;
        /** 申请项目ID (lis_order_item.id, 用于关联标本) */
        private Long orderItemId;
    }
}
