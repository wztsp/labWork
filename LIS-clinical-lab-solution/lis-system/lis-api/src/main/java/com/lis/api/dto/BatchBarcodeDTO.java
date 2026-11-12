package com.lis.api.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 批量生成条码DTO
 * 按就诊维度跨申请合管, 每组生成一个条码(一根管)
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BatchBarcodeDTO {

    /** 就诊ID (跨申请合管的主键) */
    private Long visitId;

    /** 患者ID */
    private Long patientId;

    /** 患者姓名 */
    private String patientName;

    /** 就诊号 */
    private String visitNo;

    /** 科室 */
    private String department;

    /** 试管分组列表 (每组生成一个条码) */
    private List<BarcodeGroup> groups;

    /**
     * 单个试管分组 (一个标本可关联多个申请项目)
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class BarcodeGroup {
        /** 试管ID (可能为null) */
        private Long tubeId;
        /** 试管名称 */
        private String tubeName;
        /** 标本类型 */
        private String sampleType;
        /** 容器类型 */
        private String containerType;
        /** 该管包含的检验项目信息 (含orderId和orderItemId, 用于关联标本) */
        private List<GroupTestItem> testItems;
    }

    /**
     * 分组内的检验项目 (含关联信息)
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class GroupTestItem {
        /** 检验项目ID */
        private Long testItemId;
        /** 申请ID */
        private Long orderId;
        /** 申请项目ID (lis_order_item.id) */
        private Long orderItemId;
    }
}
