package com.lis.api.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ResultDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    private Long sampleId;

    /** 样本号/条码 (仪器解析得到, 用于匹配标本) */
    private String sampleNo;

    private Long testItemId;

    private String testItemCode;

    private String testItemName;

    private String resultValue;

    private String resultText;

    private String unit;

    private String refRange;

    private String abnormalFlag;

    private Integer isCritical;

    private Long instrumentId;

    private Long operatorId;

    private LocalDateTime testTime;

    /** 试剂批次ID (试剂追溯) */
    private Long reagentLotId;

    /** 试剂批号 (试剂追溯) */
    private String reagentLotNo;

    /** 结果备注 (质控分流失败等场景的标记) */
    private String remark;
}
