package com.lis.qc.controller;

import com.lis.common.core.result.R;
import com.lis.qc.service.IqcService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * 室内质控(IQC)管理接口 —— 依据 iqc-module-design.md。
 */
@RestController
@RequestMapping("/api/qc/iqc")
@RequiredArgsConstructor
public class IqcController {

    private final IqcService iqcService;

    @GetMapping("/summary")
    public R<Map<String, Object>> summary() {
        return R.ok(iqcService.summary());
    }

    @GetMapping("/projects")
    public R<List<Map<String, Object>>> projects(
            @RequestParam(required = false) Long instrumentId,
            @RequestParam(required = false) String group,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String keyword) {
        return R.ok(iqcService.projectList(instrumentId, group, status, keyword));
    }

    @GetMapping("/projects/{lotId}")
    public R<Map<String, Object>> projectDetail(@PathVariable Long lotId) {
        return R.ok(iqcService.projectDetail(lotId));
    }
}