package com.lis.api.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OrderDTO {

    private String orderNo;

    private Long visitId;

    private Long patientId;

    private String patientName;

    private String orderType;

    private String applyDoctor;

    private String applyDept;

    private String clinicalDiag;

    /** 患者生理条件: 如 女性-卵泡期/立位/晨8点/空腹/孕妇-孕早期 等 */
    private String patientCondition;

    /** 采集方式ID (关联 lis_collection_method.id) */
    private Long collectionMethodId;

    /** 采集方式名称 (冗余, 便于直接展示) */
    private String collectionMethodName;

    private List<OrderItemDTO> items;
}
