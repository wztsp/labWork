package com.lis.report.controller;

import com.lis.common.core.result.R;
import com.lis.report.dto.BatchAuditDTO;
import com.lis.report.dto.BatchModifyInspectorDTO;
import com.lis.report.dto.BatchModifyResultDTO;
import com.lis.report.dto.BatchOpResult;
import com.lis.report.service.TechnicianService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 技师工作站 Controller
 * 核收后检验结果为空, 结果值通过通讯程序回传
 */
@RestController
@RequestMapping("/api/report/technician")
@RequiredArgsConstructor
public class TechnicianController {

    private final TechnicianService technicianService;

    /** 查询已核收标本列表（兼容两种访问路径：/api/report/technician 与 /api/report/technician/samples） */
    @GetMapping("")
    public R<List<Map<String, Object>>> listByPath(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Integer status,
            @RequestParam(required = false) String visitType,
            @RequestParam(required = false) String startDate,
            @RequestParam(required = false) String endDate,
            @RequestParam(required = false) Long instrumentId,
            @RequestParam(required = false) String receiveInstrumentId,
            @RequestParam(required = false) String receiveDate,
            @RequestParam(defaultValue = "1") int current,
            @RequestParam(defaultValue = "500") int size) {
        // 兼容旧前端参数名（receiveInstrumentId / receiveDate）
        Long actualInstrumentId = instrumentId;
        if (actualInstrumentId == null && receiveInstrumentId != null && !receiveInstrumentId.isBlank()) {
            try { actualInstrumentId = Long.valueOf(receiveInstrumentId.trim()); } catch (NumberFormatException ignored) {}
        }
        String actualStart = startDate;
        String actualEnd = endDate;
        if ((actualStart == null || actualStart.isEmpty()) && receiveDate != null && !receiveDate.isBlank()) {
            actualStart = receiveDate.trim();
            actualEnd = receiveDate.trim();
        }
        List<Map<String, Object>> list = technicianService.getSamples(
                keyword, visitType, actualStart, actualEnd, current, size, actualInstrumentId);
        return R.ok(list);
    }

    /** 查询已核收标本列表 */
    @GetMapping("/samples")
    public R<List<Map<String, Object>>> samples(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Integer status,
            @RequestParam(required = false) String visitType,
            @RequestParam(required = false) String startDate,
            @RequestParam(required = false) String endDate,
            @RequestParam(required = false) Long instrumentId,
            @RequestParam(defaultValue = "1") int current,
            @RequestParam(defaultValue = "500") int size) {
        List<Map<String, Object>> list = technicianService.getSamples(keyword, visitType, startDate, endDate, current, size, instrumentId);
        return R.ok(list);
    }

    /** 查询标本详情 (含患者信息 + 检验结果/指标) */
    @GetMapping("/{sampleId}")
    public R<Map<String, Object>> detail(@PathVariable Long sampleId) {
        return R.ok(technicianService.getDetail(sampleId));
    }

    /** 保存检验结果 */
    @PostMapping("/{sampleId}/results")
    public R<Void> saveResults(@PathVariable Long sampleId, @RequestBody List<Map<String, Object>> results) {
        technicianService.saveResults(sampleId, results);
        return R.ok();
    }

    /** 单标本终审/驳回 (通过后报告完成, 临床端可见) */
    @PutMapping("/{sampleId}/audit")
    public R<Boolean> audit(@PathVariable Long sampleId, @RequestBody Map<String, Object> body,
                            @RequestHeader(value = "X-User-Id", required = false) Long userId) {
        boolean pass = body.get("pass") != null
                && (Boolean.TRUE.equals(body.get("pass")) || "true".equals(String.valueOf(body.get("pass"))));
        String reason = body.get("reason") != null ? String.valueOf(body.get("reason")) : null;
        BatchAuditDTO dto = new BatchAuditDTO();
        dto.setSampleIds(java.util.List.of(sampleId));
        dto.setPass(pass);
        dto.setReason(reason);
        BatchOpResult result = technicianService.batchAudit(dto, userId);
        if (result.getFailed() > 0) {
            String msg = (result.getMessages() != null && !result.getMessages().isEmpty())
                    ? String.join("; ", result.getMessages()) : "终审失败";
            return R.fail(msg);
        }
        return R.ok(true);
    }

    /** 统计汇总 */
    @GetMapping("/summary")
    public R<Map<String, Object>> summary(
            @RequestParam(required = false) Long instrumentId,
            @RequestParam(required = false) String startDate,
            @RequestParam(required = false) String endDate) {
        return R.ok(technicianService.getSummary(instrumentId, startDate, endDate));
    }

    /* ============ 批量操作接口 ============ */

    /** 批量审核 */
    @PostMapping("/batch-audit")
    public R<BatchOpResult> batchAudit(@RequestBody BatchAuditDTO dto,
                                       @RequestHeader(value = "X-User-Id", required = false) Long userId) {
        return R.ok(technicianService.batchAudit(dto, userId));
    }

    /** 批量撤销审核 */
    @PostMapping("/batch-unaudit")
    public R<BatchOpResult> batchUnaudit(@RequestBody List<Long> sampleIds) {
        return R.ok(technicianService.batchUnaudit(sampleIds));
    }

    /** 批量修改结果 */
    @PostMapping("/batch-modify-result")
    public R<BatchOpResult> batchModifyResult(@RequestBody BatchModifyResultDTO dto) {
        return R.ok(technicianService.batchModifyResult(dto));
    }

    /** 批量修改检验人 */
    @PostMapping("/batch-modify-inspector")
    public R<BatchOpResult> batchModifyInspector(@RequestBody BatchModifyInspectorDTO dto) {
        return R.ok(technicianService.batchModifyInspector(dto));
    }

    /** 批量补打条码 */
    @PostMapping("/batch-reprint-barcode")
    public R<BatchOpResult> batchReprintBarcode(@RequestBody Map<String, Object> params) {
        return R.ok(technicianService.batchReprintBarcode(params));
    }

    /** 让步核收 */
    @PostMapping("/waive-receive")
    public R<BatchOpResult> waiveReceive(@RequestBody Map<String, Object> params) {
        return R.ok(technicianService.waiveReceive(params));
    }

    /** 标本复制 */
    @PostMapping("/sample-copy")
    public R<BatchOpResult> sampleCopy(@RequestBody Map<String, Object> params) {
        return R.ok(technicianService.sampleCopy(params));
    }

    /** 标本拆分 */
    @PostMapping("/sample-split")
    public R<BatchOpResult> sampleSplit(@RequestBody Map<String, Object> params) {
        return R.ok(technicianService.sampleSplit(params));
    }

    /** 撤销拆分 */
    @PostMapping("/sample-unsplit")
    public R<BatchOpResult> sampleUnsplit(@RequestBody Map<String, Object> params) {
        return R.ok(technicianService.sampleUnsplit(params));
    }

    /** 标本销毁 */
    @PostMapping("/sample-destroy")
    public R<BatchOpResult> sampleDestroy(@RequestBody Map<String, Object> params) {
        return R.ok(technicianService.sampleDestroy(params));
    }

    /** 仪器/人员签到 */
    @PostMapping("/instrument-signin")
    public R<BatchOpResult> instrumentSignin(@RequestBody Map<String, Object> params) {
        return R.ok(technicianService.instrumentSignin(params));
    }

    /** 自动审核签到 */
    @PostMapping("/auto-audit-signin")
    public R<BatchOpResult> autoAuditSignin(@RequestBody Map<String, Object> params) {
        return R.ok(technicianService.autoAuditSignin(params));
    }

    /* ============ 收藏夹 ============ */

    /** 查询收藏列表 */
    @GetMapping("/favorites")
    public R<List<Map<String, Object>>> favorites() {
        return R.ok(technicianService.getFavorites());
    }

    /** 新增收藏 */
    @PostMapping("/favorites")
    public R<Map<String, Object>> addFavorite(@RequestBody Map<String, Object> params) {
        return R.ok(technicianService.addFavorite(params));
    }

    /** 删除收藏 */
    @DeleteMapping("/favorites/{id}")
    public R<Void> removeFavorite(@PathVariable Long id) {
        technicianService.removeFavorite(id);
        return R.ok();
    }

    /* ============ 撤销核收 / 修改标本号 ============ */

    /** 撤销核收 (支持批量) */
    @PostMapping("/cancel-receive")
    public R<BatchOpResult> cancelReceive(@RequestBody List<Long> sampleIds) {
        return R.ok(technicianService.cancelReceive(sampleIds));
    }

    /** 修改标本号 */
    @PutMapping("/{sampleId}/sample-no")
    public R<BatchOpResult> updateSampleNo(@PathVariable Long sampleId,
                                            @RequestParam String sampleNo) {
        return R.ok(technicianService.updateSampleNo(sampleId, sampleNo));
    }

    /** 修改标本信息 (标本号/核收时间/核收人/检验时间/采集时间, 仅核收未审核可改) */
    @PutMapping("/{sampleId}/info")
    public R<BatchOpResult> updateSampleInfo(@PathVariable Long sampleId,
                                             @RequestBody Map<String, Object> params) {
        return R.ok(technicianService.updateSampleInfo(sampleId, params));
    }
}
