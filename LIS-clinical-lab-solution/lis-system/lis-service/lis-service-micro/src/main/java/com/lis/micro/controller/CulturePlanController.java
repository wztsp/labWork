package com.lis.micro.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.lis.common.core.result.PageResult;
import com.lis.common.core.result.R;
import com.lis.micro.dto.SavePlanRequest;
import com.lis.micro.entity.CulturePlan;
import com.lis.micro.service.CulturePlanService;
import com.lis.micro.vo.CulturePlanVO;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 培养方案 Controller
 */
@RestController
@RequestMapping("/api/micro/culture-plan")
@RequiredArgsConstructor
public class CulturePlanController {

    private final CulturePlanService culturePlanService;

    @GetMapping
    public R<PageResult<CulturePlan>> page(@RequestParam(defaultValue = "1") long current,
                                           @RequestParam(defaultValue = "20") long size,
                                           @RequestParam(required = false) String keyword) {
        Page<CulturePlan> page = culturePlanService.queryPage(current, size, keyword);
        return R.ok(new PageResult<>(page.getRecords(), page.getTotal(), page.getCurrent(), page.getSize()));
    }

    @GetMapping("/{id:\\d+}")
    public R<CulturePlanVO> detail(@PathVariable Long id) {
        return R.ok(culturePlanService.getDetail(id));
    }

    @GetMapping("/by-item/{testItemId}")
    public R<List<CulturePlan>> byItem(@PathVariable Long testItemId) {
        return R.ok(culturePlanService.listByTestItemId(testItemId));
    }

    /** 全部启用培养方案 */
    @GetMapping("/all")
    public R<List<CulturePlan>> all() {
        return R.ok(culturePlanService.lambdaQuery()
                .eq(CulturePlan::getStatus, 1)
                .orderByAsc(CulturePlan::getPlanName)
                .list());
    }

    @PostMapping
    public R<CulturePlan> create(@RequestBody SavePlanRequest req) {
        return R.ok(culturePlanService.savePlan(req.getPlan(), req.getItems()));
    }

    @PutMapping
    public R<CulturePlan> update(@RequestBody SavePlanRequest req) {
        return R.ok(culturePlanService.savePlan(req.getPlan(), req.getItems()));
    }

    @DeleteMapping("/{id:\\d+}")
    public R<Void> delete(@PathVariable Long id) {
        culturePlanService.deletePlan(id);
        return R.ok();
    }
}