package com.lis.reagent.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.lis.common.core.result.PageResult;
import com.lis.common.core.result.R;
import com.lis.reagent.dto.ApprovalDTO;
import com.lis.reagent.dto.RequisitionCreateDTO;
import com.lis.reagent.dto.StockOpContext;
import com.lis.reagent.entity.Reagent;
import com.lis.reagent.entity.ReagentRequisition;
import com.lis.reagent.service.ReagentRequisitionService;
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
 * 统一出入库业务单 Controller（领用 / 退库 / 报废 / 调拨 / 借用 / 归还，R-05）。
 *
 * <p>「领用」为本批主场景：创建草稿 → 提交（普通免审）→ 执行（扣减库存）。
 * 危险品单强制审批（Q6）。</p>
 *
 * @author LIS Team
 */
@RestController
@RequestMapping("/api/reagent/requisition")
@RequiredArgsConstructor
public class ReagentRequisitionController {

    private final ReagentRequisitionService requisitionService;
    private final ReagentService reagentService;

    /**
     * 下载请领业务单明细导入模板（CSV）。
     */
    @GetMapping("/template")
    public void template(HttpServletResponse response) throws IOException {
        List<List<String>> sample = new ArrayList<>();
        sample.add(List.of("R26_GLU", "10", "mL", "检验科领用"));
        FileImportUtil.writeCsvTemplate(response, "requisition-template.csv",
                "试剂编码,数量,单位,备注", sample);
    }

    /**
     * 解析请领业务单明细导入文件，返回可填充到明细表格的行及行级错误。
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
                items.add(buildItem(cols));
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
        boolean hazard = reagent.getHazardFlag() != null && reagent.getHazardFlag() == 1;

        Map<String, Object> item = new LinkedHashMap<>();
        item.put("reagentId", reagent.getId());
        item.put("reagentCode", reagent.getReagentCode());
        item.put("reagentName", reagent.getReagentName());
        item.put("specification", reagent.getSpecification());
        item.put("quantity", qty);
        item.put("unit", firstNonBlank(FileImportUtil.cell(cols, 2), reagent.getUnit()));
        item.put("remark", FileImportUtil.cell(cols, 3));
        item.put("hazardFlag", hazard ? 1 : 0);
        item.put("lotNo", "");
        item.put("expiryDate", "");
        return item;
    }

    private String firstNonBlank(String a, String b) {
        if (a != null && !a.isEmpty()) return a;
        return b;
    }

    /** 分页查询 */
    @GetMapping("/page")
    public R<PageResult<ReagentRequisition>> page(@RequestParam(defaultValue = "1") long current,
                                                  @RequestParam(defaultValue = "10") long size,
                                                  @RequestParam(required = false) Integer reqType,
                                                  @RequestParam(required = false) Integer status,
                                                  @RequestParam(required = false) String reqNo,
                                                  @RequestParam(required = false) Long deptId) {
        Page<ReagentRequisition> page = requisitionService.queryPage(current, size, reqType, status, reqNo, deptId);
        return R.ok(new PageResult<>(page.getRecords(), page.getTotal(), page.getCurrent(), page.getSize()));
    }

    /** 详情（含明细） */
    @GetMapping("/{id}")
    public R<ReagentRequisition> getDetail(@PathVariable Long id) {
        return R.ok(requisitionService.getDetail(id));
    }

    /** 创建 / 编辑草稿 */
    @PostMapping
    public R<ReagentRequisition> create(@RequestBody RequisitionCreateDTO dto) {
        return R.ok(requisitionService.create(dto));
    }

    /** 提交（普通免审 / 危险品进审批） */
    @PostMapping("/{id}/submit")
    public R<Void> submit(@PathVariable Long id,
                          @RequestParam(required = false) Long operatorId,
                          @RequestParam(required = false) String operatorName) {
        requisitionService.submit(id, new StockOpContext(operatorId, operatorName));
        return R.ok();
    }

    /** 审批通过 */
    @PostMapping("/{id}/approve")
    public R<Void> approve(@PathVariable Long id, @RequestBody ApprovalDTO dto) {
        requisitionService.approve(id, dto);
        return R.ok();
    }

    /** 审批驳回 */
    @PostMapping("/{id}/reject")
    public R<Void> reject(@PathVariable Long id, @RequestBody ApprovalDTO dto) {
        requisitionService.reject(id, dto);
        return R.ok();
    }

    /** 执行（扣减 / 增加库存，写流水；与库存变更同事务） */
    @PostMapping("/{id}/execute")
    public R<Void> execute(@PathVariable Long id,
                           @RequestParam(required = false) Long operatorId,
                           @RequestParam(required = false) String operatorName,
                           @RequestParam(required = false) Long deptId,
                           @RequestParam(required = false) String deptName) {
        StockOpContext ctx = StockOpContext.builder()
                .operatorId(operatorId)
                .operatorName(operatorName)
                .deptId(deptId)
                .deptName(deptName)
                .build();
        requisitionService.execute(id, ctx);
        return R.ok();
    }

    /** 作废 */
    @PostMapping("/{id}/cancel")
    public R<Void> cancel(@PathVariable Long id,
                          @RequestParam(required = false) String operatorName) {
        requisitionService.cancel(id, operatorName);
        return R.ok();
    }
}
