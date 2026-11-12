package com.lis.report.controller;

import com.lis.api.dto.ResultDTO;
import com.lis.api.vo.ResultVO;
import com.lis.common.core.result.R;
import com.lis.report.service.ResultService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 检验结果 Controller
 */
@RestController
@RequestMapping("/api/report/results")
@RequiredArgsConstructor
public class ResultController {

    private final ResultService resultService;

    /** 保存单条结果 */
    @PostMapping
    public R<Long> save(@RequestBody ResultDTO dto) {
        return R.ok(resultService.saveResult(dto));
    }

    /** 批量保存结果 */
    @PostMapping("/batch")
    public R<List<Long>> batchSave(@RequestBody List<ResultDTO> dtos) {
        return R.ok(resultService.batchSaveResults(dtos));
    }

    /** 根据标本 ID 查询结果列表 */
    @GetMapping("/sample/{sampleId}")
    public R<List<ResultVO>> getBySampleId(@PathVariable Long sampleId) {
        return R.ok(resultService.getResultsBySampleId(sampleId));
    }

    /** 趋势分析: 查询该患者该项目最近20次结果 */
    @GetMapping("/trend")
    public R<List<Map<String, Object>>> trend(@RequestParam Long patientId,
                                              @RequestParam Long testItemId) {
        return R.ok(resultService.trendAnalysis(patientId, testItemId));
    }
}
