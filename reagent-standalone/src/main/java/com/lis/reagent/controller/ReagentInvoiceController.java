package com.lis.reagent.controller;

import com.lis.common.core.result.PageResult;
import com.lis.common.core.result.R;
import com.lis.reagent.entity.ReagentInvoice;
import com.lis.reagent.service.ReagentInvoiceService;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

/**
 * 供应商发票 Controller。
 */
@RestController
@RequestMapping("/api/reagent/invoice")
@RequiredArgsConstructor
public class ReagentInvoiceController {

    private final ReagentInvoiceService invoiceService;

    /**
     * 发票分页查询。
     */
    @GetMapping("/page")
    public R<PageResult<ReagentInvoice>> page(
            @RequestParam(defaultValue = "1") long current,
            @RequestParam(defaultValue = "10") long size,
            @RequestParam(required = false) String invoiceNo,
            @RequestParam(required = false) Long supplierId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {
        return R.ok(invoiceService.page(current, size, invoiceNo, supplierId, startDate, endDate));
    }

    /**
     * 发票登记（发票号唯一，重复报错）。
     */
    @PostMapping
    public R<ReagentInvoice> register(@RequestBody ReagentInvoice invoice) {
        return R.ok(invoiceService.register(invoice));
    }

    /**
     * 发票修改。
     */
    @PutMapping
    public R<ReagentInvoice> update(@RequestBody ReagentInvoice invoice) {
        return R.ok(invoiceService.update(invoice));
    }

    /**
     * 逻辑作废发票（status = 0）。
     */
    @DeleteMapping("/{id}")
    public R<Void> cancel(@PathVariable Long id) {
        invoiceService.cancel(id);
        return R.ok();
    }
}
