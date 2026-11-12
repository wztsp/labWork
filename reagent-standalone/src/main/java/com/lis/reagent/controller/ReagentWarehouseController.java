package com.lis.reagent.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.lis.common.core.result.PageResult;
import com.lis.common.core.result.R;
import com.lis.reagent.dto.WarehouseCreateDTO;
import com.lis.reagent.entity.ReagentWarehouse;
import com.lis.reagent.service.ReagentWarehouseService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 试剂仓库 / 冰箱 / 层架 Controller（R-09）。
 *
 * <p><b>默认库房哨兵约定</b>：{@code id=0} 为系统内置默认库房（待归集），
 * 存量库存 {@code warehouse_id=0} 即默认库房；查询 / 操作一律按
 * {@code warehouse_id = 0} 处理存量，<b>禁止 IS NULL</b>。</p>
 *
 * @author LIS Team
 */
@RestController
@RequestMapping("/api/reagent/warehouse")
@RequiredArgsConstructor
public class ReagentWarehouseController {

    private final ReagentWarehouseService warehouseService;

    /** 仓库树（库房 → 冰箱 → 层架） */
    @GetMapping("/tree")
    public R<List<ReagentWarehouse>> tree() {
        return R.ok(warehouseService.tree());
    }

    /** 分页查询 */
    @GetMapping("/page")
    public R<PageResult<ReagentWarehouse>> page(@RequestParam(defaultValue = "1") long current,
                                                @RequestParam(defaultValue = "10") long size,
                                                @RequestParam(required = false) String keyword,
                                                @RequestParam(required = false) Integer status,
                                                @RequestParam(required = false) Integer whType) {
        Page<ReagentWarehouse> page = warehouseService.queryPage(current, size, keyword, status, whType);
        return R.ok(new PageResult<>(page.getRecords(), page.getTotal(), page.getCurrent(), page.getSize()));
    }

    /** 详情 */
    @GetMapping("/{id}")
    public R<ReagentWarehouse> getById(@PathVariable Long id) {
        return R.ok(warehouseService.getById(id));
    }

    /** 新增节点 */
    @PostMapping
    public R<ReagentWarehouse> create(@RequestBody WarehouseCreateDTO dto) {
        return R.ok(warehouseService.create(dto));
    }

    /** 更新节点 */
    @PutMapping
    public R<ReagentWarehouse> update(@RequestBody WarehouseCreateDTO dto) {
        return R.ok(warehouseService.update(dto));
    }

    /** 删除节点（默认库房 / 有子节点 / 有余量库存时拒绝） */
    @DeleteMapping("/{id}")
    public R<Void> delete(@PathVariable Long id) {
        warehouseService.delete(id);
        return R.ok();
    }
}
