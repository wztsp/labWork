package com.lis.system.controller;

import com.lis.common.core.result.R;
import com.lis.common.security.RequirePermission;
import com.lis.system.entity.SysDepartment;
import com.lis.system.service.SysDepartmentService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 科室管理 REST API
 */
@RestController
@RequestMapping("/api/system/departments")
@RequirePermission("system:user")
public class DepartmentController {

    private final SysDepartmentService sysDepartmentService;

    @Autowired
    public DepartmentController(SysDepartmentService sysDepartmentService) {
        this.sysDepartmentService = sysDepartmentService;
    }

    /** 树形列表 */
    @GetMapping
    public R<List<SysDepartment>> tree() {
        return R.ok(sysDepartmentService.listTree());
    }

    /** 扁平列表 (调试用，不构建树，查看原始parentId) */
    @GetMapping("/flat")
    public R<List<SysDepartment>> flat() {
        List<SysDepartment> all = sysDepartmentService.list();
        // 清空children避免干扰
        all.forEach(d -> d.setChildren(null));
        return R.ok(all);
    }

    /** 新增科室 */
    @PostMapping
    public R<SysDepartment> create(@RequestBody SysDepartment dept) {
        return R.ok(sysDepartmentService.create(dept));
    }

    /** 修改科室 */
    @PutMapping("/{id}")
    public R<Void> update(@PathVariable Long id, @RequestBody SysDepartment dept) {
        dept.setId(id);
        sysDepartmentService.update(dept);
        return R.ok();
    }

    /** 删除科室 */
    @DeleteMapping("/{id}")
    public R<Void> delete(@PathVariable Long id) {
        sysDepartmentService.delete(id);
        return R.ok();
    }
}
