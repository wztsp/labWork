package com.lis.reagent.print.controller;

import com.lis.common.core.result.R;
import com.lis.reagent.print.service.PrintDataService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 单据打印数据。
 *
 * <p>统一出口：前端只按 {@code header/items} 取字段，模板与单据类型解耦。</p>
 *
 * @author LIS Team
 */
@RestController
@RequestMapping("/api/reagent/print-data")
@RequiredArgsConstructor
public class PrintDataController {

    private final PrintDataService printDataService;

    /**
     * 取单据打印数据。
     *
     * @param bizType 业务类型；报损单传 SCRAP_DISPOSAL，id 传报废单（领用单）ID
     * @param id      单据 ID
     */
    @GetMapping("/{bizType}/{id}")
    public R<Map<String, Object>> data(@PathVariable String bizType, @PathVariable Long id) {
        return R.ok(printDataService.buildData(bizType, id));
    }
}
