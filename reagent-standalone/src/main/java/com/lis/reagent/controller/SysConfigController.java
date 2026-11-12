package com.lis.reagent.controller;

import com.lis.common.core.result.R;
import com.lis.reagent.entity.SysConfig;
import com.lis.reagent.service.SysConfigService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 系统参数配置 Controller。
 */
@RestController
@RequestMapping("/api/reagent/config")
@RequiredArgsConstructor
public class SysConfigController {

    private final SysConfigService sysConfigService;

    /** 全部参数 */
    @GetMapping("/list")
    public R<List<SysConfig>> list() {
        return R.ok(sysConfigService.listAll());
    }

    /** 按参数键取值 */
    @GetMapping("/{key}")
    public R<String> getValue(@PathVariable String key) {
        return R.ok(sysConfigService.getValue(key));
    }

    /** 更新参数值（参数不存在时报错） */
    @PutMapping
    public R<Void> update(@RequestBody Map<String, String> body) {
        sysConfigService.updateValue(body.get("configKey"), body.get("configValue"));
        return R.ok();
    }
}
