package com.lis.system.controller;

import com.lis.common.annotation.OperLog;
import com.lis.common.core.result.PageResult;
import com.lis.common.core.result.R;
import com.lis.common.security.RequirePermission;
import com.lis.system.entity.SysRole;
import com.lis.system.service.SysRoleService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * 角色管理 REST API
 */
@RestController
@RequestMapping("/api/system/roles")
@RequirePermission("system:role")
public class RoleController {

    private final SysRoleService sysRoleService;

    @Autowired
    public RoleController(SysRoleService sysRoleService) {
        this.sysRoleService = sysRoleService;
    }

    /**
     * 分页查询角色
     */
    @GetMapping
    public R<PageResult<SysRole>> page(
            @RequestParam(defaultValue = "1") long current,
            @RequestParam(defaultValue = "10") long size,
            @RequestParam(required = false) String roleName,
            @RequestParam(required = false) Integer status) {
        return R.ok(sysRoleService.page(current, size, roleName, status));
    }

    /**
     * 查询全部启用角色 (用于分配角色下拉框)
     */
    @GetMapping("/all")
    public R<List<SysRole>> listAll() {
        return R.ok(sysRoleService.listEnabled());
    }

    /**
     * 新增角色
     */
    @PostMapping
    @OperLog(module = "角色管理", operation = "新增角色", recordParams = true)
    public R<SysRole> create(@RequestBody SysRole role) {
        return R.ok(sysRoleService.create(role));
    }

    /**
     * 修改角色
     */
    @PutMapping
    @OperLog(module = "角色管理", operation = "修改角色", recordParams = true)
    public R<Void> update(@RequestBody SysRole role) {
        sysRoleService.update(role);
        return R.ok();
    }

    /**
     * 删除角色
     */
    @DeleteMapping("/{id}")
    @OperLog(module = "角色管理", operation = "删除角色")
    public R<Void> delete(@PathVariable Long id) {
        sysRoleService.delete(id);
        return R.ok();
    }

    /**
     * 给角色分配权限
     */
    @PutMapping("/{id}/permissions")
    @OperLog(module = "角色管理", operation = "分配权限")
    public R<Void> assignPermissions(@PathVariable Long id, @RequestBody Map<String, List<Long>> body) {
        List<Long> permissionIds = body.get("permissionIds");
        sysRoleService.assignPermissions(id, permissionIds);
        return R.ok();
    }
}
