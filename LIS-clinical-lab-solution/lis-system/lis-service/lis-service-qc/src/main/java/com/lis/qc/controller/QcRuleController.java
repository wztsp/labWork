package com.lis.qc.controller;

import com.lis.common.core.result.R;
import com.lis.qc.entity.QcRule;
import com.lis.qc.service.QcRuleService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Westgard 规则配置 Controller
 */
@RestController
@RequestMapping("/api/qc/rules")
@RequiredArgsConstructor
public class QcRuleController {

    private final QcRuleService qcRuleService;

    @GetMapping
    public R<List<QcRule>> listAll() {
        return R.ok(qcRuleService.listAll());
    }

    @GetMapping("/enabled")
    public R<List<QcRule>> listEnabled() {
        return R.ok(qcRuleService.listEnabled());
    }

    @GetMapping("/{id}")
    public R<QcRule> getById(@PathVariable Long id) {
        return R.ok(qcRuleService.getById(id));
    }

    @PostMapping
    public R<Long> create(@RequestBody QcRule rule) {
        return R.ok(qcRuleService.create(rule));
    }

    @PutMapping
    public R<Boolean> update(@RequestBody QcRule rule) {
        return R.ok(qcRuleService.update(rule));
    }

    @DeleteMapping("/{id}")
    public R<Boolean> delete(@PathVariable Long id) {
        return R.ok(qcRuleService.delete(id));
    }

    /** 启停规则: enabled=1启用 0禁用 */
    @PutMapping("/{id}/enabled")
    public R<Boolean> toggleEnabled(@PathVariable Long id, @RequestParam int enabled) {
        return R.ok(qcRuleService.toggleEnabled(id, enabled));
    }
}
