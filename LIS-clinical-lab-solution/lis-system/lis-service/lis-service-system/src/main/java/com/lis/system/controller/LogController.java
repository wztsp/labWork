package com.lis.system.controller;

import com.lis.common.core.result.PageResult;
import com.lis.common.core.result.R;
import com.lis.common.security.RequirePermission;
import com.lis.system.entity.SysAuditLog;
import com.lis.system.service.SysAuditLogService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 操作日志 REST API
 */
@RestController
@RequestMapping("/api/system/logs")
@RequirePermission("system:log")
public class LogController {

    private final SysAuditLogService sysAuditLogService;

    @Autowired
    public LogController(SysAuditLogService sysAuditLogService) {
        this.sysAuditLogService = sysAuditLogService;
    }

    /**
     * 分页查询操作日志
     */
    @GetMapping
    public R<PageResult<SysAuditLog>> page(
            @RequestParam(defaultValue = "1") long current,
            @RequestParam(defaultValue = "10") long size,
            @RequestParam(required = false) String username,
            @RequestParam(required = false) String module) {
        return R.ok(sysAuditLogService.page(current, size, username, module));
    }

    /**
     * 分页查询操作日志 (兼容 /page 路径)
     */
    @GetMapping("/page")
    public R<PageResult<SysAuditLog>> pageAlias(
            @RequestParam(defaultValue = "1") long current,
            @RequestParam(defaultValue = "10") long size,
            @RequestParam(required = false) String username,
            @RequestParam(required = false) String module) {
        return R.ok(sysAuditLogService.page(current, size, username, module));
    }
}
