package com.lis.api.vo;

import lombok.Data;

/**
 * 标本图像 VO (血常规/尿常规等形态图像, 一个标本对应多张)
 */
@Data
public class SampleImageVO {

    private Long id;

    /** 标本ID */
    private Long sampleId;

    /** 图像URL */
    private String imageUrl;

    /** 图像名称/说明 (如 图1-细胞形态) */
    private String imageName;

    /** 图像类型 (如 CELL_MORPHOLOGY/URINE_SEDIMENT) */
    private String imageType;

    /** 排序号 */
    private Integer sortNo;
}