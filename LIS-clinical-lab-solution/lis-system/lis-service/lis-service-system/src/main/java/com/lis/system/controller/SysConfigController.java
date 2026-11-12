package com.lis.system.controller;

import com.lis.common.core.result.R;
import com.lis.common.security.RequirePermission;
import com.lis.system.entity.SysConfig;
import com.lis.system.service.SysConfigService;
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
 * 系统参数 REST API
 */
@RestController
@RequestMapping("/api/system/configs")
@RequirePermission("system:user")
public class SysConfigController {

    private final SysConfigService sysConfigService;

    @Autowired
    public SysConfigController(SysConfigService sysConfigService) {
        this.sysConfigService = sysConfigService;
    }

    /** 列表 */
    @GetMapping
    public R<List<SysConfig>> list() {
        return R.ok(sysConfigService.listAll());
    }

    /** 按 key 查询 */
    @GetMapping("/{key}")
    public R<SysConfig> getByKey(@PathVariable String key) {
        return R.ok(sysConfigService.getByKey(key));
    }

    /** 新增 */
    @PostMapping
    public R<SysConfig> create(@RequestBody SysConfig config) {
        return R.ok(sysConfigService.create(config));
    }

    /** 修改 */
    @PutMapping("/{id}")
    public R<Void> update(@PathVariable Long id, @RequestBody SysConfig config) {
        config.setId(id);
        sysConfigService.update(config);
        return R.ok();
    }

    /** 删除 */
    @DeleteMapping("/{id}")
    public R<Void> delete(@PathVariable Long id) {
        sysConfigService.delete(id);
        return R.ok();
    }
}
