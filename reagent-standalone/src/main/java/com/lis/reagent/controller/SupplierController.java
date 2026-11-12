package com.lis.reagent.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.lis.common.core.result.PageResult;
import com.lis.common.core.result.R;
import com.lis.reagent.dto.SupplierCreateDTO;
import com.lis.reagent.entity.Supplier;
import com.lis.reagent.service.SupplierService;
import com.lis.reagent.vo.PriceCompareVO;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 供应商主数据 Controller（R-04）。
 *
 * <p>供应商为通用主数据（仪器模块未来可复用，主理人 Q1 裁决），
 * 路由统一挂在 {@code /api/reagent/supplier} 下。</p>
 *
 * @author LIS Team
 */
@RestController
@RequestMapping("/api/reagent/supplier")
@RequiredArgsConstructor
public class SupplierController {

    private final SupplierService supplierService;

    /** 分页查询 */
    @GetMapping("/page")
    public R<PageResult<Supplier>> page(@RequestParam(defaultValue = "1") long current,
                                        @RequestParam(defaultValue = "10") long size,
                                        @RequestParam(required = false) String keyword,
                                        @RequestParam(required = false) Integer status,
                                        @RequestParam(required = false) Integer qualStatus,
                                        @RequestParam(required = false) Integer sourceType) {
        Page<Supplier> page = supplierService.queryPage(current, size, keyword, status, qualStatus, sourceType);
        return R.ok(new PageResult<>(page.getRecords(), page.getTotal(), page.getCurrent(), page.getSize()));
    }

    /** 详情 */
    @GetMapping("/{id}")
    public R<Supplier> getById(@PathVariable Long id) {
        return R.ok(supplierService.getById(id));
    }

    /** 新增（手工录入） */
    @PostMapping
    public R<Supplier> create(@RequestBody SupplierCreateDTO dto) {
        return R.ok(supplierService.create(dto));
    }

    /** 更新 */
    @PutMapping
    public R<Supplier> update(@RequestBody SupplierCreateDTO dto) {
        return R.ok(supplierService.update(dto));
    }

    /** 删除（被引用时拒绝） */
    @DeleteMapping("/{id}")
    public R<Void> delete(@PathVariable Long id) {
        supplierService.delete(id);
        return R.ok();
    }

    /** 采购下拉：证照有效且启用的供应商 */
    @GetMapping("/selectable")
    public R<List<Supplier>> selectable() {
        return R.ok(supplierService.listSelectable());
    }

    /** 证照临期筛选（30 天内到期或已过期） */
    @GetMapping("/expiring")
    public R<List<Supplier>> expiring(@RequestParam(defaultValue = "30") int days) {
        return R.ok(supplierService.listExpiringWithin(days));
    }

    /** 同试剂跨供应商历史比价（按均价升序） */
    @GetMapping("/compare")
    public R<PriceCompareVO> compare(@RequestParam Long reagentId) {
        return R.ok(supplierService.comparePrice(reagentId));
    }
}
