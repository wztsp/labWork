package com.lis.hr.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.lis.common.core.result.PageResult;
import com.lis.common.core.result.R;
import com.lis.hr.entity.LabDocument;
import com.lis.hr.service.LabDocumentService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

/**
 * 文档资料 Controller
 */
@RestController
@RequestMapping("/api/hr/documents")
@RequiredArgsConstructor
public class LabDocumentController {

    private final LabDocumentService documentService;

    @GetMapping
    public R<PageResult<LabDocument>> page(@RequestParam(defaultValue = "1") long current,
                                            @RequestParam(defaultValue = "20") long size,
                                            @RequestParam(required = false) String keyword,
                                            @RequestParam(required = false) String docType,
                                            @RequestParam(required = false) Long groupId) {
        Page<LabDocument> page = documentService.queryPage(current, size, keyword, docType, groupId);
        return R.ok(new PageResult<>(page.getRecords(), page.getTotal(), page.getCurrent(), page.getSize()));
    }

    @GetMapping("/{id}")
    public R<LabDocument> getById(@PathVariable Long id) {
        return R.ok(documentService.getById(id));
    }

    @PostMapping
    public R<LabDocument> create(@RequestBody LabDocument document) {
        return R.ok(documentService.create(document));
    }

    @PutMapping
    public R<LabDocument> update(@RequestBody LabDocument document) {
        return R.ok(documentService.update(document));
    }

    @PostMapping("/{id}/abolish")
    public R<Void> abolish(@PathVariable Long id) {
        documentService.abolish(id);
        return R.ok();
    }

    @DeleteMapping("/{id}")
    public R<Void> delete(@PathVariable Long id) {
        documentService.delete(id);
        return R.ok();
    }
}
