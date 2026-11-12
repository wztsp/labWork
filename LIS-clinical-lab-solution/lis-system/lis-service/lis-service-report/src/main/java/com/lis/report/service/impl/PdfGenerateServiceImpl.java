package com.lis.report.service.impl;

import com.lis.api.vo.ReportVO;
import com.lis.api.vo.ResultVO;
import com.lis.common.core.exception.BusinessException;
import com.lis.report.entity.Report;
import com.lis.report.mapper.ReportMapper;
import com.lis.report.service.PdfGenerateService;
import com.lis.report.service.ReportService;
import com.openhtmltopdf.pdfboxout.PdfRendererBuilder;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * PDF 生成服务实现 (使用 OpenHTMLtoPDF 渲染真实 PDF)
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PdfGenerateServiceImpl implements PdfGenerateService {

    private final ReportMapper reportMapper;
    private final ReportService reportService;

    @Value("${report.pdf.storage-path:/data/lis/pdf}")
    private String storagePath;

    /** 常见 CJK 字体路径, 用于渲染中文 */
    private static final String[] CJK_FONT_PATHS = {
            "C:/Windows/Fonts/simsun.ttc",
            "C:/Windows/Fonts/msyh.ttc",
            "C:/Windows/Fonts/simhei.ttf",
            "/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf",
            "/usr/share/fonts/opentype/noto/NotoSansCJK-Regular.ttc",
            "/usr/share/fonts/noto-cjk/NotoSansCJK-Regular.ttc"
    };

    @Override
    public String generatePdf(Long reportId) {
        ReportVO vo = reportService.getById(reportId);
        if (vo == null) {
            throw new BusinessException("报告不存在: " + reportId);
        }
        try {
            Path dir = Paths.get(storagePath);
            Files.createDirectories(dir);
            String fileName = "report_" + reportId + "_"
                    + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss")) + ".pdf";
            Path file = dir.resolve(fileName);

            // 构建 HTML 模板
            String html = buildHtml(vo);

            // 使用 OpenHTMLtoPDF 渲染 PDF
            PdfRendererBuilder builder = new PdfRendererBuilder();
            builder.useFastMode();
            registerCjkFont(builder);
            builder.withHtmlContent(html, null);
            try (OutputStream os = Files.newOutputStream(file)) {
                builder.toStream(os);
                builder.run();
            }

            // 回写 PDF 路径
            Report report = reportMapper.selectById(reportId);
            if (report != null) {
                report.setPdfPath(file.toString());
                report.setUpdateTime(LocalDateTime.now());
                reportMapper.updateById(report);
            }
            log.info("生成 PDF 报告: {}", file);
            return file.toString();
        } catch (Exception e) {
            log.error("生成 PDF 失败: reportId={}", reportId, e);
            throw new BusinessException("生成 PDF 失败: " + e.getMessage());
        }
    }

    /**
     * 注册 CJK 字体, 确保中文能正常渲染; 找不到则使用默认字体
     */
    private void registerCjkFont(PdfRendererBuilder builder) {
        for (String path : CJK_FONT_PATHS) {
            File fontFile = new File(path);
            if (fontFile.exists() && fontFile.canRead()) {
                try {
                    builder.useFont(fontFile, "CJK");
                    log.debug("注册 CJK 字体: {}", path);
                    return;
                } catch (Exception e) {
                    log.warn("注册字体失败: {}", path);
                }
            }
        }
        log.warn("未找到 CJK 字体, 中文可能无法正常渲染");
    }

    /**
     * 构建报告 HTML 模板 (报告头、患者信息、结果表格、参考范围、审核签名)
     */
    private String buildHtml(ReportVO vo) {
        DateTimeFormatter fmt = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
        StringBuilder sb = new StringBuilder();
        sb.append("<!DOCTYPE html><html><head><meta charset=\"UTF-8\"/>");
        sb.append("<style>");
        sb.append("@page { size: A4; margin: 15mm; }");
        sb.append("body { font-family: 'CJK', sans-serif; font-size: 12px; color: #000; }");
        sb.append(".report-header { text-align: center; border-bottom: 2px solid #000; padding-bottom: 8px; margin-bottom: 12px; }");
        sb.append(".report-title { font-size: 20px; font-weight: bold; margin: 4px 0; }");
        sb.append(".report-hospital { font-size: 13px; color: #555; }");
        sb.append(".info-grid { display: table; width: 100%; margin-bottom: 12px; }");
        sb.append(".info-row { display: table-row; }");
        sb.append(".info-cell { display: table-cell; padding: 3px 6px; font-size: 12px; width: 50%; }");
        sb.append(".info-label { color: #555; }");
        sb.append(".info-value { font-weight: bold; }");
        sb.append("table.results { width: 100%; border-collapse: collapse; margin-bottom: 12px; }");
        sb.append("table.results th, table.results td { border: 1px solid #999; padding: 4px 6px; text-align: center; font-size: 12px; }");
        sb.append("table.results th { background: #f0f0f0; font-weight: bold; }");
        sb.append(".critical { color: #f00; font-weight: bold; }");
        sb.append(".flag-h { color: #f00; }");
        sb.append(".flag-l { color: #e80; }");
        sb.append(".footer { margin-top: 20px; font-size: 12px; }");
        sb.append(".signatures { display: table; width: 100%; margin-top: 10px; }");
        sb.append(".sig-cell { display: table-cell; text-align: center; width: 33%; padding: 6px; }");
        sb.append(".note { text-align: center; color: #888; font-size: 11px; margin-top: 8px; }");
        sb.append("</style></head><body>");

        // 报告头
        sb.append("<div class=\"report-header\">");
        sb.append("<div class=\"report-title\">检验报告单</div>");
        sb.append("<div class=\"report-hospital\">临床检验信息系统</div>");
        sb.append("</div>");

        // 患者信息
        sb.append("<div class=\"info-grid\">");
        appendInfoRow(sb, "报告编号", vo.getReportNo() != null ? vo.getReportNo() : "-");
        appendInfoRow(sb, "标本ID", vo.getSampleId() != null ? String.valueOf(vo.getSampleId()) : "-");
        appendInfoRow(sb, "患者姓名", vo.getPatientName() != null ? vo.getPatientName() : "-");
        appendInfoRow(sb, "报告类型", vo.getReportType() != null ? String.valueOf(vo.getReportType()) : "-");
        appendInfoRow(sb, "报告医生", vo.getReportDoctor() != null ? vo.getReportDoctor() : "-");
        appendInfoRow(sb, "审核者ID", vo.getAuditorId() != null ? String.valueOf(vo.getAuditorId()) : "-");
        appendInfoRow(sb, "发布时间", vo.getPublishTime() != null ? vo.getPublishTime().format(fmt) : "-");
        appendInfoRow(sb, "创建时间", vo.getCreateTime() != null ? vo.getCreateTime().format(fmt) : "-");
        sb.append("</div>");

        // 结果表格
        sb.append("<table class=\"results\">");
        sb.append("<thead><tr>");
        sb.append("<th>项目编码</th><th>项目名称</th><th>结果</th><th>单位</th>");
        sb.append("<th>参考范围</th><th>异常标识</th><th>危急值</th>");
        sb.append("</tr></thead><tbody>");
        if (vo.getResults() != null) {
            for (ResultVO r : vo.getResults()) {
                sb.append("<tr>");
                sb.append("<td>").append(escape(r.getTestItemCode())).append("</td>");
                sb.append("<td>").append(escape(r.getTestItemName())).append("</td>");
                boolean critical = r.getIsCritical() != null && r.getIsCritical() == 1;
                sb.append("<td").append(critical ? " class=\"critical\"" : "").append(">")
                        .append(escape(r.getResultValue())).append("</td>");
                sb.append("<td>").append(escape(r.getUnit())).append("</td>");
                sb.append("<td>").append(escape(r.getRefRange())).append("</td>");
                String flag = r.getAbnormalFlag();
                String flagClass = "H".equals(flag) ? "flag-h" : ("L".equals(flag) ? "flag-l" : "");
                sb.append("<td").append(!flagClass.isEmpty() ? " class=\"" + flagClass + "\"" : "")
                        .append(">").append(flag != null ? escape(flag) : "-").append("</td>");
                sb.append("<td>").append(critical ? "是" : "否").append("</td>");
                sb.append("</tr>");
            }
        }
        sb.append("</tbody></table>");

        // 审核签名
        sb.append("<div class=\"footer\">");
        sb.append("<div class=\"signatures\">");
        sb.append("<div class=\"sig-cell\">检验者：").append(escape(vo.getChecker())).append("</div>");
        sb.append("<div class=\"sig-cell\">审核者ID：")
                .append(vo.getAuditorId() != null ? vo.getAuditorId() : "-").append("</div>");
        sb.append("<div class=\"sig-cell\">报告医生：").append(escape(vo.getReportDoctor())).append("</div>");
        sb.append("</div>");
        sb.append("<div class=\"note\">※ 本报告仅对来样负责，如有疑问请在收到报告后 24 小时内提出</div>");
        sb.append("</div>");

        sb.append("</body></html>");
        return sb.toString();
    }

    private void appendInfoRow(StringBuilder sb, String label, String value) {
        sb.append("<div class=\"info-row\">");
        sb.append("<div class=\"info-cell\"><span class=\"info-label\">").append(label)
                .append("：</span><span class=\"info-value\">").append(escape(value)).append("</span></div>");
        sb.append("</div>");
    }

    private String escape(String s) {
        if (s == null) return "";
        return s.replace("&", "&amp;").replace("<", "&lt;")
                .replace(">", "&gt;").replace("\"", "&quot;");
    }
}
