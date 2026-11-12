package com.lis.micro.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.lis.common.core.result.PageResult;
import com.lis.common.core.result.R;
import com.lis.micro.entity.ResistanceRule;
import com.lis.micro.service.ResistanceRuleService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 特殊耐药规则 Controller
 */
@RestController
@RequestMapping("/api/micro/resistance-rules")
@RequiredArgsConstructor
public class ResistanceRuleController {

    private final ResistanceRuleService resistanceRuleService;

    @GetMapping
    public R<PageResult<ResistanceRule>> page(@RequestParam(defaultValue = "1") long current,
                                               @RequestParam(defaultValue = "20") long size,
                                               @RequestParam(required = false) String keyword,
                                               @RequestParam(required = false) String ruleType) {
        Page<ResistanceRule> page = resistanceRuleService.queryPage(current, size, keyword, ruleType);
        return R.ok(new PageResult<>(page.getRecords(), page.getTotal(), page.getCurrent(), page.getSize()));
    }

    @GetMapping("/all")
    public R<List<ResistanceRule>> listAll() {
        return R.ok(resistanceRuleService.listAllEnabled());
    }

    /**
     * 根据菌株编码匹配耐药规则
     */
    @GetMapping("/match/{organismCode}")
    public R<List<ResistanceRule>> matchByOrganism(@PathVariable String organismCode) {
        return R.ok(resistanceRuleService.matchByOrganism(organismCode));
    }

    @GetMapping("/{id}")
    public R<ResistanceRule> getById(@PathVariable Long id) {
        return R.ok(resistanceRuleService.getById(id));
    }

    @PostMapping
    public R<ResistanceRule> create(@RequestBody ResistanceRule rule) {
        return R.ok(resistanceRuleService.create(rule));
    }

    @PutMapping
    public R<ResistanceRule> update(@RequestBody ResistanceRule rule) {
        return R.ok(resistanceRuleService.update(rule));
    }

    @DeleteMapping("/{id}")
    public R<Void> delete(@PathVariable Long id) {
        resistanceRuleService.delete(id);
        return R.ok();
    }
}
