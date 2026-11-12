package com.lis.report.service;

/**
 * 报告 PDF 生成服务
 */
public interface PdfGenerateService {

    /**
     * 生成报告 PDF，返回生成的 PDF 文件路径
     */
    String generatePdf(Long reportId);
}
