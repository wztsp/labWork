package com.lis.reagent.thirdparty.dto;

import com.lis.reagent.thirdparty.entity.ThirdPartyFieldMap;
import lombok.Data;

import java.util.List;

/**
 * 字段映射整组保存入参。
 */
@Data
public class FieldMapBatchDTO {

    private String partnerCode;
    private String bizType;
    private Integer direction;
    private List<ThirdPartyFieldMap> maps;
}
