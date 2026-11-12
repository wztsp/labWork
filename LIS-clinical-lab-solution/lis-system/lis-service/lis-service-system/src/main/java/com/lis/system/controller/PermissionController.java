package com.lis.system.controller;

import com.lis.common.core.result.R;
import com.lis.common.security.RequirePermission;
import com.lis.system.entity.SysPermission;
import com.lis.system.service.SysPermissionService;
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
 * 权限管理 REST API
 */
@RestController
@RequestMapping("/api/system/permissions")
@RequirePermission("system:perm")
public class PermissionController {

    private final SysPermissionService sysPermissionService;

    @Autowired
    public PermissionController(SysPermissionService sysPermissionService) {
        this.sysPermissionService = sysPermissionService;
    }

    /**
     * 获取全部权限列表 (扁平)
     */
    @GetMapping
    public R<List<SysPermission>> list() {
        return R.ok(sysPermissionService.list());
    }

    /**
     * 获取权限树
     */
    @GetMapping("/tree")
    public R<List<SysPermissionService.PermissionNode>> tree() {
        return R.ok(sysPermissionService.tree());
    }

    /**
     * 新增权限
     */
    @PostMapping
    public R<SysPermission> create(@RequestBody SysPermission permission) {
        return R.ok(sysPermissionService.create(permission));
    }

    /**
     * 修改权限
     */
    @PutMapping
    public R<Void> update(@RequestBody SysPermission permission) {
        sysPermissionService.update(permission);
        return R.ok();
    }

    /**
     * 删除权限
     */
    @DeleteMapping("/{id}")
    public R<Void> delete(@PathVariable Long id) {
        sysPermissionService.delete(id);
        return R.ok();
    }
}
