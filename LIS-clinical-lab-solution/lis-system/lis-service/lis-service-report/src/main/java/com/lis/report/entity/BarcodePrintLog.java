package com.lis.report.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.LocalDateTime;

/**
 * 条码打印/补打记录
 * 对应表 lis_barcode_print_log, 建表脚本见 docs/database/add-technician-signin-print.sql
 */
@TableName("lis_barcode_print_log")
public class BarcodePrintLog {

    /** 打印类型: FIRST=首次打印 */
    public static final String TYPE_FIRST = "FIRST";
    /** 打印类型: REPRINT=补打 */
    public static final String TYPE_REPRINT = "REPRINT";

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 标本ID, 关联 lis_sample.id */
    private Long sampleId;

    /** 标本条码 (冗余存储, 便于溯源打印当时的条码值) */
    private String sampleBarcode;

    /** 打印类型: FIRST 首次 / REPRINT 补打 */
    private String printType;

    /** 打印份数 */
    private Integer copies;

    /** 打印机名称 */
    private String printerName;

    /** 补打原因 */
    private String reason;

    /** 操作人ID */
    private Long operatorId;

    /** 操作人姓名 */
    private String operatorName;

    /** 打印时间 */
    private LocalDateTime printTime;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getSampleId() {
        return sampleId;
    }

    public void setSampleId(Long sampleId) {
        this.sampleId = sampleId;
    }

    public String getSampleBarcode() {
        return sampleBarcode;
    }

    public void setSampleBarcode(String sampleBarcode) {
        this.sampleBarcode = sampleBarcode;
    }

    public String getPrintType() {
        return printType;
    }

    public void setPrintType(String printType) {
        this.printType = printType;
    }

    public Integer getCopies() {
        return copies;
    }

    public void setCopies(Integer copies) {
        this.copies = copies;
    }

    public String getPrinterName() {
        return printerName;
    }

    public void setPrinterName(String printerName) {
        this.printerName = printerName;
    }

    public String getReason() {
        return reason;
    }

    public void setReason(String reason) {
        this.reason = reason;
    }

    public Long getOperatorId() {
        return operatorId;
    }

    public void setOperatorId(Long operatorId) {
        this.operatorId = operatorId;
    }

    public String getOperatorName() {
        return operatorName;
    }

    public void setOperatorName(String operatorName) {
        this.operatorName = operatorName;
    }

    public LocalDateTime getPrintTime() {
        return printTime;
    }

    public void setPrintTime(LocalDateTime printTime) {
        this.printTime = printTime;
    }

}
