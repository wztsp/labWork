package com.lis.qc.controller;

import com.lis.common.core.result.PageResult;
import com.lis.common.core.result.R;
import com.lis.qc.entity.EqaRecord;
import com.lis.qc.service.EqaService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 室间质控 (EQA) Controller
 */
@RestController
@RequestMapping("/api/qc/eqa")
@RequiredArgsConstructor
public class EqaController {

    private final EqaService eqaService;

    /** 提交 EQA 数据 */
    @PostMapping
    public R<EqaRecord> submit(@RequestBody EqaRecord record) {
        return R.ok(eqaService.submitEqa(record));
    }

    /** 批量提交 EQA 数据 (用于手工批量生成) */
    @PostMapping("/batch")
    public R<Integer> batchSubmit(@RequestBody List<EqaRecord> records) {
        return R.ok(eqaService.batchSubmitEqa(records));
    }

    /**
     * 分页查询 (支持按批号、项目名称、仪器名称、是否合格过滤)
     * 兼容前端 batchNo/itemName/instrumentName/qualified 字段
     */
    @GetMapping("/page")
    public R<PageResult<EqaRecord>> page(
            @RequestParam(defaultValue = "1") int current,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) String eqaBatchNo,
            @RequestParam(required = false) String batchNo,
            @RequestParam(required = false) Long testItemId,
            @RequestParam(required = false) String itemName,
            @RequestParam(required = false) String testItemName,
            @RequestParam(required = false) String instrumentName,
            @RequestParam(required = false) Boolean passed,
            @RequestParam(required = false) Integer qualified) {
        // 兼容前端 batchNo 与 eqaBatchNo, itemName 与 testItemName
        String batch = (eqaBatchNo != null && !eqaBatchNo.isBlank()) ? eqaBatchNo : batchNo;
        String item = (testItemName != null && !testItemName.isBlank()) ? testItemName : itemName;
        // 兼容前端 qualified(0/1) 与 passed(Boolean)
        Boolean passedFilter = passed;
        if (passedFilter == null && qualified != null) {
            passedFilter = qualified == 1;
        }
        return R.ok(eqaService.getEqaPage(current, size, batch, testItemId, item, instrumentName, passedFilter));
    }

    /** 详情 */
    @GetMapping("/{id}")
    public R<EqaRecord> getById(@PathVariable Long id) {
        return R.ok(eqaService.getById(id));
    }

    /** 计算得分 */
    @PostMapping("/{id}/calculate")
    public R<EqaRecord> calculate(@PathVariable Long id) {
        return R.ok(eqaService.calculateScore(id));
    }

    /**
     * 合格率统计 (支持按批号、项目名称过滤)
     * 兼容前端 batchNo/itemName 字段
     */
    @GetMapping("/stats")
    public R<Map<String, Object>> stats(
            @RequestParam(required = false) String eqaBatchNo,
            @RequestParam(required = false) String batchNo,
            @RequestParam(required = false) String itemName,
            @RequestParam(required = false) String testItemName) {
        String batch = (eqaBatchNo != null && !eqaBatchNo.isBlank()) ? eqaBatchNo : batchNo;
        String item = (testItemName != null && !testItemName.isBlank()) ? testItemName : itemName;
        return R.ok(eqaService.getEqaStats(batch, item));
    }
}
