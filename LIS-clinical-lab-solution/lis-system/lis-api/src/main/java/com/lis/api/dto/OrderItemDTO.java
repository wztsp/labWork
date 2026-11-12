package com.lis.api.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OrderItemDTO {

    private Long testItemId;

    private String testItemCode;

    private String testItemName;

    private BigDecimal fee;

    /** HIS 申请唯一键(申请号#项目序号), 用于接口重复推送时幂等去重; 手工录入为空 */
    private String hisApplyId;
}
