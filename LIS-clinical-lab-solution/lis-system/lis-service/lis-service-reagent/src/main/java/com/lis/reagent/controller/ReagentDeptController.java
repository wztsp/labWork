package com.lis.reagent.controller;

import com.lis.common.core.result.R;
import com.lis.reagent.mapper.ReagentDeptMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * 试剂模块科室选项（替代主系统的 /api/system/departments，保证独立部署可用）。
 */
@RestController
@RequestMapping("/api/reagent/dept")
@RequiredArgsConstructor
public class ReagentDeptController {

    private final ReagentDeptMapper deptMapper;

    @GetMapping("/options")
    public R<List<Map<String, Object>>> options() {
        return R.ok(deptMapper.selectDeptOptions());
    }
}
