package com.lis.instrument.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 标本 (仅用于仪器结果匹配查询 lis_sample 表)
 * 匹配字段: sample_barcode / sample_no
 */
@Data
@TableName("lis_sample")
public class Sample implements Serializable {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    private String sampleBarcode;

    private String sampleNo;

    private Long orderId;

    private Long patientId;

    private String patientName;

    private Integer status;

    private LocalDateTime createTime;
}
