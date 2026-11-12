package com.lis.report.controller;

import com.lis.api.dto.ReportAuditDTO;
import com.lis.api.dto.ResultDTO;
import com.lis.api.vo.ReportVO;
import com.lis.common.annotation.OperLog;
import com.lis.common.core.result.PageResult;
import com.lis.common.core.result.R;
import com.lis.report.dto.ReportModifyReqDTO;
import com.lis.report.entity.Result;
import com.lis.report.mapper.ResultMapper;
import com.lis.report.service.PdfGenerateService;
import com.lis.report.service.ReportService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.List;

/**
 * 报告管理 Controller
 */
@RestController
@RequestMapping("/api/report")
@RequiredArgsConstructor
public class ReportController {

    private final ReportService reportService;
    private final PdfGenerateService pdfGenerateService;
    private final ResultMapper resultMapper;

    /** 分页查询 (支持报告查询页: 日期范围/关键字/就诊类型/科室/标本号/条码号/就诊号/患者ID) */
    @GetMapping("/page")
    public R<PageResult<ReportVO>> page(
            @RequestParam(defaultValue = "1") int current,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String patientName,
            @RequestParam(required = false) Integer status,
            @RequestParam(required = false) String startDate,
            @RequestParam(required = false) String endDate,
            @RequestParam(required = false) Long patientId,
            @RequestParam(required = false) Integer visitType,
            @RequestParam(required = false) String department,
            @RequestParam(required = false) String sampleNo,
            @RequestParam(required = false) String sampleBarcode,
            @RequestParam(required = false) String visitNo,
            @RequestParam(required = false) String keyword) {
        return R.ok(reportService.queryPage(current, size, patientName, status,
                startDate, endDate, patientId, visitType, department,
                sampleNo, sampleBarcode, visitNo, keyword));
    }

    /** 根据 ID 获取报告（仅限纯数字 ID，避免 /api/report/technician 等路径被误匹配为 {id}） */
    @GetMapping("/{id:\\d+}")
    public R<ReportVO> getById(@PathVariable Long id) {
        return R.ok(reportService.getById(id));
    }

    /** 根据标本 ID 获取报告 */
    @GetMapping("/sample/{sampleId}")
    public R<ReportVO> getBySampleId(@PathVariable Long sampleId) {
        return R.ok(reportService.getBySampleId(sampleId));
    }

    /** 根据标本 ID 生成报告草稿 */
    @PostMapping("/generate/{sampleId}")
    @OperLog(module = "报告管理", operation = "生成报告")
    public R<ReportVO> generate(@PathVariable Long sampleId) {
        return R.ok(reportService.generateReport(sampleId));
    }

    /** 自动审核 */
    @PutMapping("/audit/auto/{reportId}")
    @OperLog(module = "报告管理", operation = "自动审核")
    public R<Boolean> autoAudit(@PathVariable Long reportId) {
        return R.ok(reportService.autoAudit(reportId));
    }

    /** 人工审核 */
    @PutMapping("/audit")
    @OperLog(module = "报告管理", operation = "人工审核", recordParams = true)
    public R<Boolean> manualAudit(@RequestBody ReportAuditDTO dto) {
        return R.ok(reportService.manualAudit(dto));
    }

    /** 发布报告 (按 reportId 查询参数) */
    @PutMapping("/publish")
    @OperLog(module = "报告管理", operation = "发布报告")
    public R<Boolean> publish(@RequestParam Long reportId, @RequestParam Long publisherId) {
        return R.ok(reportService.publish(reportId, publisherId));
    }

    /** 发布报告 (RESTful 路径参数, 兼容旧接口) */
    @PutMapping("/{id:\\d+}/publish")
    @OperLog(module = "报告管理", operation = "发布报告")
    public R<Boolean> publishById(@PathVariable Long id, @RequestParam Long publisherId) {
        return R.ok(reportService.publish(id, publisherId));
    }

    /** 修改报告 (留痕, 按 resultId 修改) */
    @PutMapping("/modify")
    public R<Boolean> modify(@RequestBody ReportModifyReqDTO req) {
        List<ResultDTO> newResults = new ArrayList<>();
        String reason = "";
        if (req.getResults() != null) {
            for (ReportModifyReqDTO.ModifyItem item : req.getResults()) {
                if (item.getResultId() == null) {
                    continue;
                }
                // 通过 resultId 查询现有结果, 取得其 testItemId 以复用既有服务逻辑
                Result existing = resultMapper.selectById(item.getResultId());
                if (existing == null) {
                    continue;
                }
                ResultDTO dto = new ResultDTO();
                dto.setTestItemId(existing.getTestItemId());
                dto.setResultValue(item.getResultValue());
                dto.setOperatorId(existing.getOperatorId());
                newResults.add(dto);
                if (item.getModifyReason() != null && !item.getModifyReason().isBlank() && reason.isEmpty()) {
                    reason = item.getModifyReason();
                }
            }
        }
        if (reason.isEmpty()) {
            reason = "前端修改结果";
        }
        return R.ok(reportService.modifyReport(req.getReportId(), newResults, reason));
    }

    /** 修改报告 (RESTful 路径参数, 兼容旧接口) */
    @PutMapping("/{id:\\d+}/modify")
    public R<Boolean> modifyById(@PathVariable Long id,
                             @RequestBody List<ResultDTO> newResults,
                             @RequestParam String reason) {
        return R.ok(reportService.modifyReport(id, newResults, reason));
    }

    /** 生成报告 PDF */
    @PostMapping("/{id:\\d+}/pdf")
    public R<String> generatePdf(@PathVariable Long id) {
        return R.ok(pdfGenerateService.generatePdf(id));
    }

    /** 合并多份标本报告到目标标本 */
    @PostMapping("/merge")
    public R<ReportVO> merge(@RequestParam List<Long> sampleIds, @RequestParam Long targetSampleId) {
        return R.ok(reportService.mergeReports(sampleIds, targetSampleId));
    }
}
