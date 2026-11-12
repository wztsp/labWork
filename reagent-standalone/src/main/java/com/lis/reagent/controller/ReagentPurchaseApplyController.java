package com.lis.reagent.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.lis.common.core.result.PageResult;
import com.lis.common.core.result.R;
import com.lis.reagent.dto.ApplyCreateDTO;
import com.lis.reagent.dto.ApprovalDTO;
import com.lis.reagent.dto.StockOpContext;
import com.lis.reagent.entity.Reagent;
import com.lis.reagent.entity.ReagentPurchaseApply;
import com.lis.reagent.service.ReagentPurchaseApplyService;
import com.lis.reagent.service.ReagentService;
import com.lis.reagent.support.FileImportUtil;
import com.lis.reagent.vo.ApplyDetailVO;
import com.lis.reagent.vo.SubmitResultVO;
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
 * 采购申请 Controller（R-02）。
 *
 * <p>审批动作（通过 / 驳回）的 action 取值见 {@code com.lis.reagent.enums.ApprovalAction}：
 * 1 通过 / 2 驳回。审批意见经 {@link ApprovalDTO#getOpinion()} 透传。</p>
 *
 * @author LIS Team
 */
@RestController
@RequestMapping("/api/reagent/purchase-apply")
@RequiredArgsConstructor
public class ReagentPurchaseApplyController {

    private final ReagentPurchaseApplyService applyService;
    private final ReagentService reagentService;

    /**
     * 下载采购申请明细导入模板（CSV）。
     */
    @GetMapping("/template")
    public void template(HttpServletResponse response) throws IOException {
        List<List<String>> sample = new ArrayList<>();
        sample.add(List.of("R26_GLU", "50", "mL", "35.00", "补充生化葡萄糖试剂"));
        FileImportUtil.writeCsvTemplate(response, "purchase-apply-template.csv",
                "试剂编码,数量,单位,预估单价,备注", sample);
    }

    /**
     * 解析采购申请明细导入文件，返回可填充到明细表格的行及行级错误。
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
        String priceStr = FileImportUtil.cell(cols, 3);

        Map<String, Object> item = new LinkedHashMap<>();
        item.put("reagentId", reagent.getId());
        item.put("reagentCode", reagent.getReagentCode());
        item.put("reagentName", reagent.getReagentName());
        item.put("specification", reagent.getSpecification());
        item.put("quantity", qty);
        item.put("unit", firstNonBlank(FileImportUtil.cell(cols, 2), reagent.getUnit()));
        item.put("estimatedPrice", priceStr != null ? new BigDecimal(priceStr) : reagent.getPricePurchase());
        item.put("remark", FileImportUtil.cell(cols, 4));
        return item;
    }

    private String firstNonBlank(String a, String b) {
        if (a != null && !a.isEmpty()) return a;
        return b;
    }

    /** 分页查询 */
    @GetMapping("/page")
    public R<PageResult<ReagentPurchaseApply>> page(@RequestParam(defaultValue = "1") long current,
                                                    @RequestParam(defaultValue = "10") long size,
                                                    @RequestParam(required = false) Integer status,
                                                    @RequestParam(required = false) String applyNo,
                                                    @RequestParam(required = false) Long deptId,
                                                    @RequestParam(required = false) Long categoryId) {
        Page<ReagentPurchaseApply> page = applyService.queryPage(current, size, status, applyNo, deptId, categoryId);
        return R.ok(new PageResult<>(page.getRecords(), page.getTotal(), page.getCurrent(), page.getSize()));
    }

    /** 详情（明细 + 审批流水 + 预算 + 允许动作） */
    @GetMapping("/{id}")
    public R<ApplyDetailVO> getDetail(@PathVariable Long id) {
        return R.ok(applyService.getDetail(id));
    }

    /** 草稿保存（新建或编辑草稿 / 已驳回单） */
    @PostMapping
    public R<ReagentPurchaseApply> saveDraft(@RequestBody ApplyCreateDTO dto) {
        return R.ok(applyService.saveDraft(dto));
    }

    /** 提交（冻结预算 + 进入审批） */
    @PostMapping("/{id}/submit")
    public R<SubmitResultVO> submit(@PathVariable Long id,
                                    @RequestParam(required = false) Long operatorId,
                                    @RequestParam(required = false) String operatorName) {
        return R.ok(applyService.submit(id, new StockOpContext(operatorId, operatorName)));
    }

    /** 审批通过 */
    @PostMapping("/{id}/approve")
    public R<Void> approve(@PathVariable Long id, @RequestBody ApprovalDTO dto) {
        applyService.approve(id, dto);
        return R.ok();
    }

    /** 审批驳回（自动释放预算占用） */
    @PostMapping("/{id}/reject")
    public R<Void> reject(@PathVariable Long id, @RequestBody ApprovalDTO dto) {
        applyService.reject(id, dto);
        return R.ok();
    }

    /** 取消申请（释放预算占用） */
    @PostMapping("/{id}/cancel")
    public R<Void> cancel(@PathVariable Long id,
                          @RequestParam(required = false) Long operatorId,
                          @RequestParam(required = false) String operatorName) {
        applyService.cancel(id, new StockOpContext(operatorId, operatorName));
        return R.ok();
    }

    /** 解冻重判（预算调增后） */
    @PostMapping("/{id}/unfreeze")
    public R<SubmitResultVO> unfreeze(@PathVariable Long id,
                                      @RequestParam(required = false) Long operatorId,
                                      @RequestParam(required = false) String operatorName) {
        return R.ok(applyService.unfreeze(id, new StockOpContext(operatorId, operatorName)));
    }
}
