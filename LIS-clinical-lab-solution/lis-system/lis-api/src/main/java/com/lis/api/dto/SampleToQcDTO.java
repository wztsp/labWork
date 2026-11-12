package com.lis.api.dto;

import lombok.Data;

import java.util.List;

/**
 * 技师工作站：手工标本转质控标本请求体
 */
@Data
public class SampleToQcDTO {

    /** 来源标本ID */
    private Long sampleId;

    /** 操作人ID (技师/员工ID) */
    private Long operatorId;

    /** 待转换的"检验指标 -> 质控批号"映射列表 */
    private List<Mapping> mappings;

    @Data
    public static class Mapping {
        /** 检验指标ID */
        private Long testItemId;
        /** 质控品批号ID */
        private Long qcLotId;
    }
}