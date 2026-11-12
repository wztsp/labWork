package com.lis.reagent.controller;

import com.lis.common.core.exception.BusinessException;
import com.lis.common.core.result.R;
import com.lis.reagent.mapper.ReagentTraceMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 试剂批次追溯 Controller
 */
@RestController
@RequestMapping("/api/reagent/trace")
@RequiredArgsConstructor
public class ReagentTraceController {

    private final ReagentTraceMapper reagentTraceMapper;

    /**
     * 根据批号查询使用了该批次试剂的所有检测结果
     */
    @GetMapping("/{lotNo}")
    public R<List<Map<String, Object>>> traceByLotNo(@PathVariable String lotNo) {
        if (lotNo == null || lotNo.isBlank()) {
            throw new BusinessException("批号不能为空");
        }
        return R.ok(reagentTraceMapper.traceByLotNo(lotNo));
    }
}
