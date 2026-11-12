package com.lis.micro.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.lis.common.core.result.PageResult;
import com.lis.common.core.result.R;
import com.lis.micro.dto.AntibioticTemplateApplyVO;
import com.lis.micro.dto.AntibioticTemplateSaveRequest;
import com.lis.micro.dto.AntibioticTemplateVO;
import com.lis.micro.entity.AntibioticTemplate;
import com.lis.micro.service.AntibioticTemplateService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 抗生素药敏模板 Controller
 */
@RestController
@RequestMapping("/api/micro/antibiotic-templates")
@RequiredArgsConstructor
public class AntibioticTemplateController {

    private final AntibioticTemplateService antibioticTemplateService;

    @GetMapping
    public R<PageResult<AntibioticTemplateVO>> page(@RequestParam(defaultValue = "1") long current,
                                                    @RequestParam(defaultValue = "20") long size,
                                                    @RequestParam(required = false) String keyword,
                                                    @RequestParam(required = false) String category,
                                                    @RequestParam(required = false) Integer status) {
        Page<AntibioticTemplateVO> page = antibioticTemplateService.queryPage(current, size, keyword, category, status);
        return R.ok(new PageResult<>(page.getRecords(), page.getTotal(), page.getCurrent(), page.getSize()));
    }

    /** 按细菌查询可用的启用模板（报告页「应用模板」下拉用） */
    @GetMapping("/by-bacteria/{bacteriaId}")
    public R<List<AntibioticTemplate>> listByBacteria(@PathVariable Long bacteriaId) {
        return R.ok(antibioticTemplateService.listEnabledByBacteria(bacteriaId));
    }

    @GetMapping("/{id}")
    public R<AntibioticTemplateVO> getById(@PathVariable Long id) {
        return R.ok(antibioticTemplateService.getDetail(id));
    }

    /** 构造「应用模板」数据（针对指定细菌解析折点） */
    @GetMapping("/{id}/apply")
    public R<AntibioticTemplateApplyVO> apply(@PathVariable Long id, @RequestParam(required = false) Long bacteriaId) {
        return R.ok(antibioticTemplateService.buildApply(id, bacteriaId));
    }

    @PostMapping
    public R<Long> create(@RequestBody AntibioticTemplateSaveRequest req) {
        return R.ok(antibioticTemplateService.saveOrUpdate(req));
    }

    @PutMapping
    public R<Long> update(@RequestBody AntibioticTemplateSaveRequest req) {
        return R.ok(antibioticTemplateService.saveOrUpdate(req));
    }

    @DeleteMapping("/{id}")
    public R<Void> delete(@PathVariable Long id) {
        antibioticTemplateService.deleteById(id);
        return R.ok();
    }
}