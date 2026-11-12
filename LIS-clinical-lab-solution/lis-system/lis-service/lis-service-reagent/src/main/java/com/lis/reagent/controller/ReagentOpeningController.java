package com.lis.reagent.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.lis.common.core.result.PageResult;
import com.lis.common.core.result.R;
import com.lis.reagent.entity.ReagentOpening;
import com.lis.reagent.service.ReagentOpeningService;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * 试剂开瓶 Controller
 */
@RestController
@RequestMapping("/api/reagent/opening")
@RequiredArgsConstructor
public class ReagentOpeningController {

    private final ReagentOpeningService openingService;

    /**
     * 分页查询：支持试剂、状态、开封类型、开瓶日期区间
     * 兼容旧路径 /api/reagent/opening 与新增路径 /api/reagent/opening/page
     */
    @GetMapping({"", "/page"})
    public R<PageResult<ReagentOpening>> page(@RequestParam(defaultValue = "1") long current,
                                              @RequestParam(defaultValue = "10") long size,
                                              @RequestParam(required = false) Long reagentId,
                                              @RequestParam(required = false) Integer status,
                                              @RequestParam(required = false) Integer openingType,
                                              @RequestParam(required = false)
                                              @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
                                              @RequestParam(required = false)
                                              @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {
        Page<ReagentOpening> page = openingService.queryPage(current, size, reagentId, status,
                openingType, startDate, endDate);
        return R.ok(new PageResult<>(page.getRecords(), page.getTotal(), page.getCurrent(), page.getSize()));
    }

    @PostMapping
    public R<ReagentOpening> create(@RequestBody ReagentOpening opening) {
        return R.ok(openingService.createOpening(opening));
    }

    @PostMapping("/lock-expired")
    public R<Integer> lockExpired() {
        return R.ok(openingService.lockExpiredOpenings());
    }

    /**
     * 二次开封
     * body: { operatorId, operatorName }
     */
    @PostMapping("/{id}/reopen")
    public R<ReagentOpening> reopen(@PathVariable Long id,
                                    @RequestBody(required = false) Map<String, Object> body) {
        Map<String, Object> params = body == null ? Map.of() : body;
        return R.ok(openingService.reopen(id, parseLong(params.get("operatorId")),
                (String) params.get("operatorName")));
    }

    /**
     * 闭封
     * body: { operatorName }
     */
    @PostMapping("/{id}/seal")
    public R<Void> seal(@PathVariable Long id,
                        @RequestBody(required = false) Map<String, Object> body) {
        Map<String, Object> params = body == null ? Map.of() : body;
        openingService.seal(id, (String) params.get("operatorName"));
        return R.ok();
    }

    /** 耗尽 */
    @PostMapping("/{id}/exhaust")
    public R<Void> exhaust(@PathVariable Long id) {
        openingService.exhaust(id);
        return R.ok();
    }

    /**
     * 质控品分包
     * body: { stockId, reagentId, packCount, operatorId, operatorName }
     */
    @PostMapping("/sub-package")
    public R<List<ReagentOpening>> subPackage(@RequestBody Map<String, Object> body) {
        List<ReagentOpening> records = openingService.subPackage(
                parseLong(body.get("stockId")),
                parseLong(body.get("reagentId")),
                parseInteger(body.get("packCount")),
                parseLong(body.get("operatorId")),
                (String) body.get("operatorName"));
        return R.ok(records);
    }

    /**
     * 危化品开封申请
     * body: { reagentId, stockId, demandQty, operatorId, operatorName, remark }
     */
    @PostMapping("/hazard-apply")
    public R<ReagentOpening> hazardApply(@RequestBody Map<String, Object> body) {
        ReagentOpening opening = openingService.applyHazardOpening(
                parseLong(body.get("reagentId")),
                parseLong(body.get("stockId")),
                parseBigDecimal(body.get("demandQty")),
                parseLong(body.get("operatorId")),
                (String) body.get("operatorName"),
                (String) body.get("remark"));
        return R.ok(opening);
    }

    /**
     * 危化品开封审批通过
     * body: { approverId, approverName, opinion }
     */
    @PostMapping("/{id}/approve")
    public R<Void> approve(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        openingService.approveHazard(id, parseLong(body.get("approverId")),
                (String) body.get("approverName"), (String) body.get("opinion"));
        return R.ok();
    }

    /**
     * 危化品开封审批驳回
     * body: { approverId, approverName, opinion }
     */
    @PostMapping("/{id}/reject")
    public R<Void> reject(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        openingService.rejectHazard(id, parseLong(body.get("approverId")),
                (String) body.get("approverName"), (String) body.get("opinion"));
        return R.ok();
    }

    private BigDecimal parseBigDecimal(Object val) {
        if (val == null) return null;
        try {
            return new BigDecimal(val.toString());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private Integer parseInteger(Object val) {
        if (val == null) return null;
        try {
            if (val instanceof Number) {
                return ((Number) val).intValue();
            }
            String s = val.toString().trim();
            if (s.isEmpty()) return null;
            return Integer.parseInt(s);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /**
     * 兼容前端 JSON 中 Long 字段以 Number 或 String 两种形式传入。
     */
    private Long parseLong(Object val) {
        if (val == null) return null;
        try {
            if (val instanceof Number) {
                return ((Number) val).longValue();
            }
            String s = val.toString().trim();
            if (s.isEmpty()) return null;
            return Long.parseLong(s);
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
