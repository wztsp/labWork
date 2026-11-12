package com.lis.system.controller;

import com.lis.common.annotation.OperLog;
import com.lis.common.core.result.PageResult;
import com.lis.common.core.result.R;
import com.lis.common.security.RequirePermission;
import com.lis.system.entity.SysUser;
import com.lis.system.service.SysUserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * 用户管理 REST API
 */
@RestController
@RequestMapping("/api/system/users")
public class UserController {

    private final SysUserService sysUserService;

    @Autowired
    public UserController(SysUserService sysUserService) {
        this.sysUserService = sysUserService;
    }

    /**
     * 分页查询用户
     */
    @GetMapping
    @RequirePermission("system:user")
    public R<PageResult<SysUser>> page(
            @RequestParam(defaultValue = "1") long current,
            @RequestParam(defaultValue = "10") long size,
            @RequestParam(required = false) String username,
            @RequestParam(required = false) String realName,
            @RequestParam(required = false) Integer status) {
        return R.ok(sysUserService.page(current, size, username, realName, status));
    }

    /**
     * 获取当前登录用户信息 (由网关注入 X-User-Id 请求头)
     */
    @GetMapping("/me")
    public R<SysUser> currentUser(@RequestHeader(value = "X-User-Id", required = false) Long userId) {
        if (userId == null) {
            return R.fail("未认证");
        }
        SysUser user = sysUserService.getById(userId);
        if (user != null) {
            user.setPassword(null);
        }
        return R.ok(user);
    }

    /**
     * 新增用户
     */
    @PostMapping
    @RequirePermission("system:user")
    @OperLog(module = "用户管理", operation = "新增用户", recordParams = true)
    public R<SysUser> create(@RequestBody SysUser user,
                             @RequestParam(required = false) List<Long> roleIds) {
        return R.ok(sysUserService.create(user, roleIds));
    }

    /**
     * 修改用户
     */
    @PutMapping
    @RequirePermission("system:user")
    @OperLog(module = "用户管理", operation = "修改用户", recordParams = true)
    public R<Void> update(@RequestBody SysUser user) {
        sysUserService.update(user);
        return R.ok();
    }

    /**
     * 删除用户
     */
    @DeleteMapping("/{id}")
    @RequirePermission("system:user")
    @OperLog(module = "用户管理", operation = "删除用户")
    public R<Void> delete(@PathVariable Long id) {
        sysUserService.delete(id);
        return R.ok();
    }

    /**
     * 给用户分配角色
     */
    @PutMapping("/{id}/roles")
    @RequirePermission("system:user")
    @OperLog(module = "用户管理", operation = "分配角色")
    public R<Void> assignRoles(@PathVariable Long id, @RequestBody Map<String, List<Long>> body) {
        List<Long> roleIds = body.get("roleIds");
        sysUserService.assignRoles(id, roleIds);
        return R.ok();
    }

    /**
     * 重置密码
     */
    @PutMapping("/{id}/reset-password")
    @RequirePermission("system:user")
    @OperLog(module = "用户管理", operation = "重置密码")
    public R<Void> resetPassword(@PathVariable Long id, @RequestBody Map<String, String> body) {
        String newPassword = body.get("newPassword");
        sysUserService.resetPassword(id, newPassword);
        return R.ok();
    }
}
