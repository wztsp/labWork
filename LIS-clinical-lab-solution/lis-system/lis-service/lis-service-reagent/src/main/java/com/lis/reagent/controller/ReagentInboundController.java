package com.lis.reagent.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.lis.common.core.result.PageResult;
import com.lis.common.core.result.R;
import com.lis.reagent.entity.Reagent;
import com.lis.reagent.entity.ReagentInbound;
import com.lis.reagent.service.ReagentInboundService;
import com.lis.reagent.service.ReagentService;
import com.lis.reagent.support.FileImportUtil;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 试剂入库 Controller
 */
@RestController
@RequestMapping("/api/reagent/inbound")
@RequiredArgsConstructor
public class ReagentInboundController {

    private final ReagentInboundService inboundService;
    private final ReagentService reagentService;

    /**
     * 下载入库明细导入模板（CSV）。
     */
    @GetMapping("/template")
    public void template(HttpServletResponse response) throws IOException {
        List<List<String>> sample = new ArrayList<>();
        sample.add(List.of("R26_GLU", "20", "mL", "35.00", "L26082301", "2027-08-23", "A-01-01"));
        FileImportUtil.writeCsvTemplate(response, "reagent-inbound-template.csv",
                "试剂编码,数量,单位,采购单价,批号,效期,存放位置", sample);
    }

    /**
     * 解析入库明细导入文件（.xlsx/.csv/.txt），返回可填充到明细表格的行及行级错误。
     */
    @PostMapping("/import")
    public R<Map<String, Object>> importItems(@RequestParam("file") MultipartFile file) throws IOException {
        List<List<String>> rows = FileImportUtil.parseRows(file);
        List<Map<String, Object>> items = new ArrayList<>();
        List<Map<String, Object>> errors = new ArrayList<>();
        int success = 0;
        int failed = 0;
        for (int i = 0; i < rows.size(); i++) {
            List<String> cols = rows.get(i);
            try {
                Map<String, Object> item = buildItem(cols);
                items.add(item);
                success++;
            } catch (Exception e) {
                failed++;
                Map<String, Object> err = new LinkedHashMap<>();
                err.put("row", i + 2);
                err.put("reason", e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage());
                errors.add(err);
            }
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("items", items);
        result.put("success", success);
        result.put("failed", failed);
        result.put("errors", errors);
        return R.ok(result);
    }

    private Map<String, Object> buildItem(List<String> cols) {
        String code = FileImportUtil.cell(cols, 0);
        if (code == null) {
            throw new IllegalArgumentException("试剂编码不能为空");
        }
        Reagent reagent = reagentService.getByCode(code);
        if (reagent == null) {
            throw new IllegalArgumentException("试剂编码不存在: " + code);
        }
        String qtyStr = FileImportUtil.cell(cols, 1);
        if (qtyStr == null) {
            throw new IllegalArgumentException("数量不能为空");
        }
        BigDecimal qty = new BigDecimal(qtyStr);
        String priceStr = FileImportUtil.cell(cols, 3);
        String expiry = FileImportUtil.cell(cols, 5);

        Map<String, Object> item = new LinkedHashMap<>();
        item.put("reagentId", reagent.getId());
        item.put("reagentCode", reagent.getReagentCode());
        item.put("reagentName", reagent.getReagentName());
        item.put("specification", reagent.getSpecification());
        item.put("quantity", qty);
        item.put("unit", firstNonBlank(FileImportUtil.cell(cols, 2), reagent.getUnit()));
        item.put("purchasePrice", priceStr != null ? new BigDecimal(priceStr) : reagent.getPricePurchase());
        item.put("lotNo", FileImportUtil.cell(cols, 4));
        item.put("expiryDate", expiry);
        item.put("location", FileImportUtil.cell(cols, 6));
        return item;
    }

    private String firstNonBlank(String a, String b) {
        if (a != null && !a.isEmpty()) return a;
        return b;
    }

    @GetMapping
    public R<PageResult<ReagentInbound>> page(@RequestParam(defaultValue = "1") long current,
                                               @RequestParam(defaultValue = "10") long size,
                                               @RequestParam(required = false) String inboundNo,
                                               @RequestParam(required = false) Integer verifyStatus) {
        Page<ReagentInbound> page = inboundService.queryPage(current, size, inboundNo, verifyStatus);
        return R.ok(new PageResult<>(page.getRecords(), page.getTotal(), page.getCurrent(), page.getSize()));
    }

    @GetMapping("/{id}")
    public R<ReagentInbound> getDetail(@PathVariable Long id) {
        return R.ok(inboundService.getDetail(id));
    }

    @PostMapping
    public R<ReagentInbound> create(@RequestBody ReagentInbound inbound) {
        return R.ok(inboundService.createInbound(inbound));
    }

    @PostMapping("/{id}/verify")
    public R<ReagentInbound> verify(@PathVariable Long id,
                                    @RequestParam Long verifierId,
                                    @RequestParam String verifierName) {
        return R.ok(inboundService.verifyInbound(id, verifierId, verifierName));
    }

    @PostMapping("/{id}/reject")
    public R<ReagentInbound> reject(@PathVariable Long id,
                                    @RequestParam Long verifierId,
                                    @RequestParam String verifierName,
                                    @RequestParam String reason) {
        return R.ok(inboundService.rejectInbound(id, verifierId, verifierName, reason));
    }
}
