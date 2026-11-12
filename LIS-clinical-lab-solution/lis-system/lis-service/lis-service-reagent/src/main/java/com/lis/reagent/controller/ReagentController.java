package com.lis.reagent.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.lis.common.core.result.PageResult;
import com.lis.common.core.result.R;
import com.lis.reagent.entity.Reagent;
import com.lis.reagent.service.ReagentService;
import com.lis.reagent.support.FileImportUtil;
import com.lis.reagent.vo.PurchaseSuggestionDTO;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.OutputStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 试剂字典 Controller
 */
@RestController
@RequestMapping("/api/reagent/dict")
@RequiredArgsConstructor
public class ReagentController {

    private final ReagentService reagentService;

    @GetMapping
    public R<PageResult<Reagent>> page(@RequestParam(defaultValue = "1") long current,
                                       @RequestParam(defaultValue = "10") long size,
                                       @RequestParam(required = false) String keyword,
                                       @RequestParam(required = false) String reagentCode,
                                       @RequestParam(required = false) String reagentName,
                                       @RequestParam(required = false) Integer status,
                                       @RequestParam(required = false) Long categoryId,
                                       @RequestParam(required = false) Integer hazardFlag,
                                       @RequestParam(required = false) Long instrumentId,
                                       @RequestParam(required = false) Long testItemId,
                                       @RequestParam(required = false) BigDecimal minQuantity,
                                       @RequestParam(required = false) BigDecimal maxQuantity,
                                       @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate expiryStart,
                                       @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate expiryEnd,
                                       @RequestParam(required = false) Long warehouseId,
                                       @RequestParam(required = false) String location) {
        Page<Reagent> page = reagentService.queryPage(current, size, keyword, reagentCode, reagentName,
                status, categoryId, hazardFlag, instrumentId, testItemId,
                minQuantity, maxQuantity, expiryStart, expiryEnd, warehouseId, location);
        return R.ok(new PageResult<>(page.getRecords(), page.getTotal(), page.getCurrent(), page.getSize()));
    }

    @GetMapping("/{id}")
    public R<Reagent> getById(@PathVariable Long id) {
        return R.ok(reagentService.getById(id));
    }

    @GetMapping("/code/{code}")
    public R<Reagent> getByCode(@PathVariable String code) {
        return R.ok(reagentService.getByCode(code));
    }

    /** 采购建议（当前库存 / 库存阈值 / 建议采购量与日期） */
    @GetMapping("/{id}/purchase-suggestion")
    public R<PurchaseSuggestionDTO> purchaseSuggestion(@PathVariable Long id) {
        return R.ok(reagentService.getPurchaseSuggestion(id));
    }

    @PostMapping
    public R<Reagent> create(@RequestBody Reagent reagent) {
        reagentService.save(reagent);
        return R.ok(reagent);
    }

    @PutMapping
    public R<Reagent> update(@RequestBody Reagent reagent) {
        reagentService.updateById(reagent);
        return R.ok(reagent);
    }

    @DeleteMapping("/{id}")
    public R<Void> delete(@PathVariable Long id) {
        // 引用守卫在 Service 层：被库存/业务单明细引用时抛 BusinessException，拒绝删除
        reagentService.delete(id);
        return R.ok();
    }

    /**
     * 试剂基础信息 Excel 导出（T-19 / R-14，UTF-8 制表符分隔，无 POI 依赖）。
     *
     * @param response HTTP 响应，用于输出文件内容
     */
    @GetMapping("/export")
    public void export(HttpServletResponse response) throws IOException {
        List<Reagent> list = reagentService.list();
        StringBuilder sb = new StringBuilder();
        // 带 UTF-8 BOM，Excel 打开可正确识别中文并按逗号分列
        sb.append('\uFEFF');
        sb.append("试剂编码,试剂名称,规格,单位,厂家,分类ID,危险品,采购价,库存下限,库存上限,状态,注册证号,预期用途\r\n");
        for (Reagent r : list) {
            sb.append(FileImportUtil.csvField(r.getReagentCode())).append(',')
                    .append(FileImportUtil.csvField(r.getReagentName())).append(',')
                    .append(FileImportUtil.csvField(r.getSpecification())).append(',')
                    .append(FileImportUtil.csvField(r.getUnit())).append(',')
                    .append(FileImportUtil.csvField(r.getManufacturer())).append(',')
                    .append(FileImportUtil.csvField(r.getCategoryId())).append(',')
                    .append(FileImportUtil.csvField(r.getHazardFlag() == null ? "0" : r.getHazardFlag())).append(',')
                    .append(FileImportUtil.csvField(r.getPricePurchase())).append(',')
                    .append(FileImportUtil.csvField(r.getMinStock())).append(',')
                    .append(FileImportUtil.csvField(r.getMaxStock())).append(',')
                    .append(FileImportUtil.csvField(r.getStatus() == null ? "1" : r.getStatus())).append(',')
                    .append(FileImportUtil.csvField(r.getRegCertNo())).append(',')
                    .append(FileImportUtil.csvField(r.getExpectedUse())).append("\r\n");
        }
        response.setContentType("text/csv;charset=UTF-8");
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.setHeader("Content-Disposition", "attachment; filename=reagent-dict.csv");
        byte[] bytes = sb.toString().getBytes(StandardCharsets.UTF_8);
        try (OutputStream out = response.getOutputStream()) {
            out.write(bytes);
            out.flush();
        }
    }

    /**
     * 试剂基础信息批量导入（T-19 / R-14）。
     *
     * <p>逐行校验，失败返回行号 + 原因，不影响成功行入库；已存在编码的试剂按编码更新，否则新增。</p>
     *
     * @param file CSV/制表符分隔文本，UTF-8 编码
     * @return 导入结果（success 成功数 / failed 失败数 / errors 行级错误明细）
     */
    @PostMapping("/import")
    public R<Map<String, Object>> importFile(@RequestParam("file") MultipartFile file) throws IOException {
        // 按文件类型解析：.xlsx/.xls 走 POI，其余按文本(CSV/制表符)
        List<List<String>> rows = FileImportUtil.parseRows(file);
        List<Map<String, Object>> errors = new ArrayList<>();
        int success = 0;
        int failed = 0;
        for (int i = 0; i < rows.size(); i++) {
            List<String> cols = rows.get(i);
            try {
                Reagent reagent = new Reagent();
                reagent.setReagentCode(FileImportUtil.cell(cols, 0));
                reagent.setReagentName(FileImportUtil.cell(cols, 1));
                if (reagent.getReagentCode() == null || reagent.getReagentName() == null) {
                    throw new IllegalArgumentException("试剂编码 / 名称不能为空");
                }
                reagent.setSpecification(FileImportUtil.cell(cols, 2));
                reagent.setUnit(FileImportUtil.cell(cols, 3));
                reagent.setManufacturer(FileImportUtil.cell(cols, 4));
                reagent.setCategoryId(toLong(FileImportUtil.cell(cols, 5)));
                reagent.setHazardFlag(toInt(FileImportUtil.cell(cols, 6)));
                reagent.setPricePurchase(toBigDecimal(FileImportUtil.cell(cols, 7)));
                reagent.setMinStock(toBigDecimal(FileImportUtil.cell(cols, 8)));
                reagent.setMaxStock(toBigDecimal(FileImportUtil.cell(cols, 9)));
                reagent.setStatus(toInt(FileImportUtil.cell(cols, 10)));
                reagent.setRegCertNo(FileImportUtil.cell(cols, 11));
                reagent.setExpectedUse(FileImportUtil.cell(cols, 12));

                Reagent exist = reagentService.getByCode(reagent.getReagentCode());
                if (exist != null) {
                    reagent.setId(exist.getId());
                    reagentService.updateById(reagent);
                } else {
                    if (reagent.getStatus() == null) {
                        reagent.setStatus(1);
                    }
                    reagentService.save(reagent);
                }
                success++;
            } catch (Exception e) {
                failed++;
                Map<String, Object> err = new LinkedHashMap<>();
                err.put("row", i + 2); // 第1行为表头，数据从第2行开始
                err.put("reason", e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage());
                errors.add(err);
            }
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("success", success);
        result.put("failed", failed);
        result.put("errors", errors);
        return R.ok(result);
    }

    private Long toLong(String s) {
        if (s == null || s.trim().isEmpty()) return null;
        return Long.valueOf(s.trim());
    }

    private Integer toInt(String s) {
        if (s == null || s.trim().isEmpty()) return null;
        return Integer.valueOf(s.trim());
    }

    private BigDecimal toBigDecimal(String s) {
        if (s == null || s.trim().isEmpty()) return null;
        return new BigDecimal(s.trim());
    }
}
