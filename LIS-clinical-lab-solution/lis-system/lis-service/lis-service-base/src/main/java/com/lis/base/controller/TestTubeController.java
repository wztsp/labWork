package com.lis.base.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.lis.base.entity.TestTube;
import com.lis.base.service.TestTubeService;
import com.lis.common.core.result.PageResult;
import com.lis.common.core.result.R;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 检验试管 Controller
 */
@RestController
@RequestMapping("/api/base/test-tubes")
@RequiredArgsConstructor
public class TestTubeController {

    private final TestTubeService testTubeService;

    /**
     * 分页查询
     */
    @GetMapping
    public R<PageResult<TestTube>> page(@RequestParam(defaultValue = "1") long current,
                                         @RequestParam(defaultValue = "10") long size,
                                         @RequestParam(required = false) String keyword,
                                         @RequestParam(required = false) String tubeType,
                                         @RequestParam(required = false) Integer status) {
        Page<TestTube> page = testTubeService.queryPage(current, size, keyword, tubeType, status);
        return R.ok(new PageResult<>(page.getRecords(), page.getTotal(), page.getCurrent(), page.getSize()));
    }

    /**
     * 查询全部启用的试管 (供下拉选择)
     */
    @GetMapping("/all")
    public R<List<TestTube>> listAll() {
        return R.ok(testTubeService.listAllEnabled());
    }

    @GetMapping("/{id}")
    public R<TestTube> getById(@PathVariable Long id) {
        return R.ok(testTubeService.getById(id));
    }

    @PostMapping
    public R<TestTube> create(@RequestBody TestTube testTube) {
        return R.ok(testTubeService.create(testTube));
    }

    @PutMapping
    public R<TestTube> update(@RequestBody TestTube testTube) {
        return R.ok(testTubeService.update(testTube));
    }

    @DeleteMapping("/{id}")
    public R<Void> delete(@PathVariable Long id) {
        testTubeService.delete(id);
        return R.ok();
    }
}
