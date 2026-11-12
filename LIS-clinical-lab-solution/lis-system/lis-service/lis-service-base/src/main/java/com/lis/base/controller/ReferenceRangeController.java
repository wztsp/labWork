package com.lis.base.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.lis.base.entity.ReferenceRange;
import com.lis.base.service.ReferenceRangeService;
import com.lis.common.core.result.PageResult;
import com.lis.common.core.result.R;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;

/**
 * 参考范围 Controller
 */
@RestController
@RequestMapping("/api/base/ref-ranges")
@RequiredArgsConstructor
public class ReferenceRangeController {

    private final ReferenceRangeService referenceRangeService;

    @GetMapping
    public R<PageResult<ReferenceRange>> page(@RequestParam(defaultValue = "1") long current,
                                              @RequestParam(defaultValue = "10") long size,
                                              @RequestParam(required = false) Long testItemId,
                                              @RequestParam(required = false) String specimenType,
                                              @RequestParam(required = false) Long instrumentId) {
        Page<ReferenceRange> page = referenceRangeService.queryPage(current, size, testItemId, specimenType, instrumentId);
        return R.ok(new PageResult<>(page.getRecords(), page.getTotal(), page.getCurrent(), page.getSize()));
    }

    @PostMapping
    public R<ReferenceRange> create(@RequestBody ReferenceRange referenceRange) {
        return R.ok(referenceRangeService.create(referenceRange));
    }

    @PutMapping
    public R<ReferenceRange> update(@RequestBody ReferenceRange referenceRange) {
        return R.ok(referenceRangeService.update(referenceRange));
    }

    @DeleteMapping("/{id}")
    public R<Void> delete(@PathVariable Long id) {
        referenceRangeService.delete(id);
        return R.ok();
    }

    @GetMapping("/item/{itemId}")
    public R<List<ReferenceRange>> listByItemId(@PathVariable Long itemId) {
        return R.ok(referenceRangeService.listByItemId(itemId));
    }

    /**
     * 按项目+性别+年龄查找匹配的参考范围
     */
    @GetMapping("/match")
    public R<ReferenceRange> findMatch(@RequestParam Long testItemId,
                                       @RequestParam Integer gender,
                                       @RequestParam(required = false) BigDecimal age,
                                       @RequestParam(required = false) String conditionRemark) {
        return R.ok(referenceRangeService.findMatch(testItemId, gender, age, conditionRemark));
    }
}
