package com.lis.monolith.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.lis.common.core.result.R;
import com.lis.monolith.entity.TatRule;
import com.lis.monolith.service.TatRuleService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * TAT 规则设置
 */
@RestController
@RequestMapping("/api/tat/rule")
@RequiredArgsConstructor
public class TatRuleController {

    private final TatRuleService tatRuleService;

    @GetMapping("/page")
    public R<IPage<TatRule>> page(@RequestParam(defaultValue = "1") int page,
                                  @RequestParam(defaultValue = "10") int size,
                                  @RequestParam(required = false) String specimenType,
                                  @RequestParam(required = false) String keyword) {
        return R.ok(tatRuleService.page(page, size, specimenType, keyword));
    }

    @GetMapping("/list")
    public R<List<TatRule>> list() {
        return R.ok(tatRuleService.listAll());
    }

    @PostMapping
    public R<TatRule> create(@RequestBody TatRule rule) {
        return R.ok(tatRuleService.create(rule));
    }

    @PutMapping
    public R<TatRule> update(@RequestBody TatRule rule) {
        return R.ok(tatRuleService.update(rule));
    }

    @DeleteMapping("/{id}")
    public R<Void> delete(@PathVariable Long id) {
        tatRuleService.delete(id);
        return R.ok();
    }

    /** 是否启用 TAT 规则 */
    @GetMapping("/enabled")
    public R<Boolean> enabled() {
        return R.ok(tatRuleService.isEnabled());
    }

    /** 设置是否启用 TAT 规则 */
    @PutMapping("/enabled")
    public R<Void> setEnabled(@RequestParam boolean enabled) {
        tatRuleService.setEnabled(enabled);
        return R.ok();
    }
}