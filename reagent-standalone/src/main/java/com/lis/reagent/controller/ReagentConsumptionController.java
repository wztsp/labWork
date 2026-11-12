package com.lis.reagent.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.lis.common.core.result.PageResult;
import com.lis.common.core.result.R;
import com.lis.reagent.entity.ReagentConsumption;
import com.lis.reagent.service.ReagentConsumptionService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

/**
 * 试剂消耗 Controller
 */
@RestController
@RequestMapping("/api/reagent/consumption")
@RequiredArgsConstructor
public class ReagentConsumptionController {

    private final ReagentConsumptionService consumptionService;

    @GetMapping
    public R<PageResult<ReagentConsumption>> page(@RequestParam(defaultValue = "1") long current,
                                                   @RequestParam(defaultValue = "10") long size,
                                                   @RequestParam(required = false) Long reagentId,
                                                   @RequestParam(required = false) Integer consumeType,
                                                   @RequestParam(required = false) String relatedNo) {
        Page<ReagentConsumption> page = consumptionService.queryPage(current, size, reagentId, consumeType, relatedNo);
        return R.ok(new PageResult<>(page.getRecords(), page.getTotal(), page.getCurrent(), page.getSize()));
    }

    @PostMapping
    public R<ReagentConsumption> create(@RequestBody ReagentConsumption consumption,
                                        @RequestParam(required = false) Long instrumentId,
                                        @RequestParam(required = false) Integer testCount) {
        // 仪器与测试数支持 query 参数传入，未传时保留实体自身值
        if (instrumentId != null) {
            consumption.setInstrumentId(instrumentId);
        }
        if (testCount != null) {
            consumption.setTestCount(testCount);
        }
        return R.ok(consumptionService.createConsumption(consumption));
    }

    @PostMapping("/auto")
    public R<Void> autoConsume(@RequestParam Long testItemId,
                               @RequestParam Integer testCount,
                               @RequestParam(required = false) Long operatorId,
                               @RequestParam(required = false) String operatorName,
                               @RequestParam(required = false) String relatedNo) {
        consumptionService.autoConsume(testItemId, testCount, operatorId, operatorName, relatedNo);
        return R.ok();
    }
}
