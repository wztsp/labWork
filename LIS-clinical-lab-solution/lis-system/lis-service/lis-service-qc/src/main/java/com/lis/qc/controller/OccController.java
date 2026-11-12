package com.lis.qc.controller;

import com.lis.common.core.result.PageResult;
import com.lis.common.core.result.R;
import com.lis.qc.entity.OccEvent;
import com.lis.qc.service.OccService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * 失控报告(OCC)管理接口 —— 依据 occ-module-design.md。
 */
@RestController
@RequestMapping("/api/qc/occ")
@RequiredArgsConstructor
public class OccController {

    private final OccService occService;

    @GetMapping({"/list", "/page"})
    public R<PageResult<OccEvent>> list(
            @RequestParam(defaultValue = "1") long current,
            @RequestParam(defaultValue = "10") long size,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String range) {
        return R.ok(occService.page(current, size, status, keyword, range));
    }

    @GetMapping("/{id:\\d+}")
    public R<OccEvent> detail(@PathVariable Long id) {
        return R.ok(occService.detail(id));
    }

    /** 建档 (由失控判读触发或手工建档) */
    @PostMapping("/create")
    public R<OccEvent> create(@RequestBody OccEvent event,
                              @RequestParam(defaultValue = "admin") String operator) {
        return R.ok(occService.create(event, operator));
    }

    /** 更新某步骤: body {checklist:[{text,done}], conclusion} */
    @PutMapping("/{id}/steps/{stepNo}")
    public R<OccEvent> updateStep(@PathVariable Long id,
                                  @PathVariable Integer stepNo,
                                  @RequestBody Map<String, Object> payload,
                                  @RequestParam(defaultValue = "admin") String operator) {
        return R.ok(occService.updateStep(id, stepNo, payload, operator));
    }

    /** 确认根因: body {category, description} (服务端强制校验步骤1-4完成) */
    @PostMapping("/{id}/root-cause/confirm")
    public R<OccEvent> confirmRootCause(@PathVariable Long id,
                                        @RequestBody(required = false) Map<String, Object> payload,
                                        @RequestParam(defaultValue = "admin") String operator) {
        return R.ok(occService.confirmRootCause(id, payload, operator));
    }

    /** 审批: ?levelNo=1&approved=true&comment=&approver= */
    @PostMapping("/{id}/approve")
    public R<OccEvent> approve(@PathVariable Long id,
                               @RequestParam Integer levelNo,
                               @RequestParam boolean approved,
                               @RequestParam(required = false) String comment,
                               @RequestParam(defaultValue = "admin") String approver) {
        return R.ok(occService.approve(id, approver, levelNo, approved, comment));
    }
}