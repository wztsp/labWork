package com.lis.reagent.print.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.lis.common.core.result.PageResult;
import com.lis.common.core.result.R;
import com.lis.reagent.print.entity.PrintTemplate;
import com.lis.reagent.print.service.PrintFieldCatalog;
import com.lis.reagent.print.service.PrintTemplateService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 打印模板管理。
 *
 * @author LIS Team
 */
@RestController
@RequestMapping("/api/reagent/print-template")
@RequiredArgsConstructor
public class PrintTemplateController {

    private final PrintTemplateService printTemplateService;

    /** 分页查询 */
    @GetMapping("/page")
    public R<PageResult<PrintTemplate>> page(@RequestParam(defaultValue = "1") long current,
                                             @RequestParam(defaultValue = "10") long size,
                                             @RequestParam(required = false) String bizType,
                                             @RequestParam(required = false) String keyword,
                                             @RequestParam(required = false) Integer status) {
        Page<PrintTemplate> page = printTemplateService.queryPage(current, size, bizType, keyword, status);
        return R.ok(new PageResult<>(page.getRecords(), page.getTotal(), page.getCurrent(), page.getSize()));
    }

    /** 业务类型下拉（编码 + 名称） */
    @GetMapping("/biz-types")
    public R<List<Map<String, Object>>> bizTypes() {
        return R.ok(PrintFieldCatalog.bizTypes());
    }

    /** 字段字典：设计器左侧字段面板 */
    @GetMapping("/fields")
    public R<Map<String, Object>> fields(@RequestParam String bizType) {
        return R.ok(PrintFieldCatalog.describe(bizType));
    }

    /** 启用模板列表（打印时选模板） */
    @GetMapping("/list")
    public R<List<PrintTemplate>> list(@RequestParam(required = false) String bizType) {
        return R.ok(printTemplateService.listEnabled(bizType));
    }

    /** 详情 */
    @GetMapping("/{id}")
    public R<PrintTemplate> detail(@PathVariable Long id) {
        return R.ok(printTemplateService.getDetail(id));
    }

    /** 新增 */
    @PostMapping
    public R<PrintTemplate> create(@RequestBody PrintTemplate template) {
        template.setId(null);
        return R.ok(printTemplateService.saveTemplate(template));
    }

    /** 修改 */
    @PutMapping
    public R<PrintTemplate> update(@RequestBody PrintTemplate template) {
        return R.ok(printTemplateService.saveTemplate(template));
    }

    /** 删除 */
    @DeleteMapping("/{id}")
    public R<Void> delete(@PathVariable Long id) {
        printTemplateService.delete(id);
        return R.ok();
    }

    /** 设为默认模板 */
    @PostMapping("/{id}/set-default")
    public R<Void> setDefault(@PathVariable Long id) {
        printTemplateService.setDefault(id);
        return R.ok();
    }
}
