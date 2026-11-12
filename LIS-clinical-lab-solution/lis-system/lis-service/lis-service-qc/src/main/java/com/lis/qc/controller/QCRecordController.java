package com.lis.qc.controller;

import com.lis.api.dto.QCRecordDTO;
import com.lis.api.dto.SampleToQcDTO;
import com.lis.common.core.result.PageResult;
import com.lis.common.core.result.R;
import com.lis.qc.entity.QCRecord;
import com.lis.qc.service.QCRecordService;
import com.lis.qc.service.rule.QCResult;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 质控记录 Controller
 */
@RestController
@RequestMapping("/api/qc/records")
@RequiredArgsConstructor
public class QCRecordController {

    private final QCRecordService qcRecordService;

    /** 录入质控数据，返回 Westgard 判定结果 */
    @PostMapping
    public R<QCResult> submit(@RequestBody QCRecordDTO dto) {
        return R.ok(qcRecordService.submitRecord(dto));
    }

    @GetMapping("/page")
    public R<PageResult<QCRecord>> page(
            @RequestParam(defaultValue = "1") int current,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) Long lotId,
            @RequestParam(required = false) Long testItemId,
            @RequestParam(required = false) Long instrumentId,
            @RequestParam(required = false) String measureDate,
            @RequestParam(required = false) String beginDate,
            @RequestParam(required = false) String endDate) {
        return R.ok(qcRecordService.queryPage(current, size, lotId, testItemId, instrumentId, measureDate, beginDate, endDate));
    }

    @GetMapping("/lot/{lotId}")
    public R<List<QCRecord>> listByLot(@PathVariable Long lotId) {
        return R.ok(qcRecordService.listByLotId(lotId));
    }

    /** 更新质控记录 */
    @PutMapping("/{id}")
    public R<QCResult> update(@PathVariable Long id, @RequestBody QCRecordDTO dto) {
        return R.ok(qcRecordService.updateRecord(id, dto));
    }

    /**
     * 批量重算指定批号+时间范围内的质控记录失控判定 (质控图"计算失控"按钮).
     * 按时间顺序逐条重跑 Westgard 规则, 更新 z_score / qc_result / rule_violation.
     * @return {total, rejectCount, updatedCount}
     */
    @PostMapping("/recalculate")
    public R<Map<String, Integer>> recalculate(
            @RequestParam Long lotId,
            @RequestParam(required = false) String beginDate,
            @RequestParam(required = false) String endDate) {
        return R.ok(qcRecordService.batchRecalculate(lotId, beginDate, endDate));
    }

    /** 技师工作站：手工标本转质控标本 (检验结果 -> 质控记录) */
    @PostMapping("/from-sample")
    public R<List<QCRecord>> fromSample(@RequestBody SampleToQcDTO dto) {
        return R.ok(qcRecordService.createFromSample(dto));
    }

    /** 取消质控：删除指定标本生成的全部质控记录 */
    @DeleteMapping("/by-sample/{sampleId}")
    public R<Integer> deleteBySample(@PathVariable Long sampleId) {
        return R.ok(qcRecordService.deleteBySampleId(sampleId));
    }
}
