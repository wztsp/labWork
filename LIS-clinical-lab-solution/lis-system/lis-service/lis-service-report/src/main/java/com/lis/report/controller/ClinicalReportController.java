package com.lis.report.controller;

import com.lis.api.vo.ReportVO;
import com.lis.common.core.result.R;
import com.lis.report.service.ReportService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 临床端报告查询 Controller
 *
 * 需 JWT 鉴权 (网关已移除 /api/clinical/** 白名单), 且必须同时提供
 * 就诊号 + 身份证号双重校验, 防止按 ID 遍历或单字段猜测。
 * 仅返回已发布(STATUS_PUBLISHED)的报告
 */
@RestController
@RequestMapping("/api/clinical/reports")
@RequiredArgsConstructor
public class ClinicalReportController {

    private final ReportService reportService;

    /**
     * 按就诊号 + 身份证号双条件查询已发布报告
     *
     * @param visitNo 就诊号 (必填)
     * @param idCard  身份证号 (必填, 明文, 内部会做 SHA-256 哈希后匹配)
     */
    @GetMapping
    public R<List<ReportVO>> list(@RequestParam String visitNo,
                                  @RequestParam String idCard) {
        if (visitNo.isBlank() || idCard.isBlank()) {
            return R.fail("就诊号与身份证号均不能为空");
        }
        return R.ok(reportService.getPublishedByVisitNoAndIdCard(visitNo, idCard));
    }
}
