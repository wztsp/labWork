package com.lis.api.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 标本视图对象
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SampleVO {

    private Long id;

    private String sampleBarcode;

    /** 标本号 */
    private String sampleNo;

    /** 核收仪器ID */
    private Long instrumentId;

    /** 核收仪器名称 */
    private String instrumentName;

    private Long orderId;

    private Long patientId;

    private Long visitId;

    private String patientName;

    private String sampleType;

    private String containerType;

    private Integer status;

    private String statusText;

    private Integer isUrgent;

    private LocalDateTime collectTime;

    /** 送检时间 */
    private LocalDateTime deliverTime;

    /** 送检人 */
    private String deliverPerson;

    /** 送检人ID (关联人事员工 hr_staff.id) */
    private Long deliverPersonId;

    /** 签收时间 */
    private LocalDateTime signOffTime;

    /** 签收人 */
    private String signOffPerson;

    /** 签收人ID (关联人事员工 hr_staff.id) */
    private Long signOffPersonId;

    /** 拒收时间 */
    private LocalDateTime rejectTime;

    /** 拒收人 */
    private String rejectPerson;

    /** 拒收人ID (关联人事员工 hr_staff.id) */
    private Long rejectPersonId;

    /** 不合格类型 */
    private String rejectType;

    /** 拒收原因 */
    private String rejectReason;

    /** 拒收次数 */
    private Integer rejectCount;

    /** 就诊号 */
    private String visitNo;

    private LocalDateTime receiveTime;

    private LocalDateTime testStartTime;

    private LocalDateTime reportTime;

    private Long operatorId;

    /** 采集人ID */
    private Long collectorId;

    /** 采集人姓名 */
    private String collectorName;

    private String department;

    private String remark;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;

    /** 标本追踪: 全生命周期时间线 */
    private List<TrackNode> trackNodes;

    /** 该标本对应的检验项目列表 (一个条码可对应多个检验项目) */
    private List<OrderItemVO> orderItems;

    /**
     * 追踪节点
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TrackNode {
        private Integer status;
        private String statusText;
        private LocalDateTime time;
        private String operator;
    }

    /**
     * 申请项目 (检验项目) 视图对象
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
        private LocalDateTime createTime;
    }
}
