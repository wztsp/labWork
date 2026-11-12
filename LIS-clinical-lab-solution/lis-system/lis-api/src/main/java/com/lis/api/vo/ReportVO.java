package com.lis.api.vo;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Data
public class ReportVO {

    private Long id;

    private String reportNo;

    private Long sampleId;

    private Long patientId;

    private String patientName;

    /** 患者性别: 1男 2女 */
    private Integer patientGender;

    /** 患者年龄 */
    private Integer patientAge;

    /** 就诊类型: 1门诊 2住院 3体检 (显示值由前端映射) */
    private Integer visitType;

    /** 医院名称 (默认连平县人民医院) */
    private String hospitalName;

    /** 就诊/申请科室 */
    private String department;

    /** 申请医生 */
    private String doctor;

    /** 临床诊断 */
    private String clinicalDiag;

    /** 就诊号 */
    private String visitNo;

    /** 标本号 (sample_no) */
    private String sampleNo;

    /** 标本条码 (sample_barcode) */
    private String sampleBarcode;

    /** 核收时间 */
    private LocalDateTime receiveTime;

    /** 该报告下申请项目字符串 */
    private String testItems;

    /** 来源: 门/住/体 (和列表列对应) */
    private String source;

    private Integer reportType;

    private Integer status;

    private String statusText;

    private Long auditorId;

    private LocalDateTime auditTime;

    private Long publisherId;

    private LocalDateTime publishTime;

    private String reportDoctor;

    private String checker;

    private String pdfPath;

    private Integer hisSyncStatus;

    private LocalDateTime hisSyncTime;

    private LocalDateTime createTime;

    private List<ResultVO> results;

    /** 标本图像列表 (血常规/尿常规等形态图像, 报告打印时展示) */
    private List<SampleImageVO> sampleImages;
}
