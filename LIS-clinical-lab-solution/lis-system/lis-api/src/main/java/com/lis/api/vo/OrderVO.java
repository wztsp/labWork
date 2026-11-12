package com.lis.api.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 检验申请视图对象
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OrderVO {

    private Long id;

    /** 申请单号 */
    private String orderNo;

    /** 就诊ID */
    private Long visitId;

    /** 就诊号 */
    private String visitNo;

    /** 患者ID */
    private Long patientId;

    /** 患者姓名 */
    private String patientName;

    /** 患者性别 */
    private String patientGender;

    /** 患者年龄 */
    private String patientAge;

    /** 就诊类型 */
    private String visitType;

    /** 申请类型 */
    private String orderType;

    /** 状态: 0待接收 1已接收 2已取消 3已完成 */
    private Integer status;

    /** 申请医生 */
    private String applyDoctor;

    /** 申请科室 */
    private String applyDept;

    /** 临床诊断 */
    private String clinicalDiag;

    /** 患者生理条件: 如 女性-卵泡期/立位/晨8点/空腹/孕妇-孕早期 等 */
    private String patientCondition;

    /** 采集方式ID (关联 lis_collection_method.id) */
    private Long collectionMethodId;

    /** 采集方式名称 (冗余, 便于直接展示) */
    private String collectionMethodName;

    /** 申请时间 */
    private LocalDateTime applyTime;

    /** 创建时间 */
    private LocalDateTime createTime;

    /** 该申请对应的项目列表 */
    private List<OrderItemVO> items;

    /** 该申请已生成的标本数 (用于判断是否已生成条码) */
    private Integer sampleCount;

    /**
     * 申请项目视图对象
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class OrderItemVO {
        private Long id;
        private Long orderId;
        private Long testItemId;
        private String testItemCode;
        private String testItemName;
        private Integer status;
        private java.math.BigDecimal fee;
    }
}
