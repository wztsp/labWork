package com.lis.reagent.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.lis.common.core.result.PageResult;
import com.lis.common.core.result.R;
import com.lis.reagent.entity.ReagentOpening;
import com.lis.reagent.service.ReagentOpeningService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

/**
 * 试剂开瓶 Controller
 */
@RestController
@RequestMapping("/api/reagent/opening")
@RequiredArgsConstructor
public class ReagentOpeningController {

    private final ReagentOpeningService openingService;

    @GetMapping
    public R<PageResult<ReagentOpening>> page(@RequestParam(defaultValue = "1") long current,
                                              @RequestParam(defaultValue = "10") long size,
                                              @RequestParam(required = false) Long reagentId,
                                              @RequestParam(required = false) Integer status) {
        Page<ReagentOpening> page = openingService.queryPage(current, size, reagentId, status);
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
}
