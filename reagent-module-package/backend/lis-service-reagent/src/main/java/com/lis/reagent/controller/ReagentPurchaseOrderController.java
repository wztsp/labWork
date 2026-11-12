package com.lis.reagent.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.lis.common.core.result.PageResult;
import com.lis.common.core.result.R;
import com.lis.reagent.dto.OrderCreateDTO;
import com.lis.reagent.dto.StockOpContext;
import com.lis.reagent.entity.Reagent;
import com.lis.reagent.entity.ReagentPurchaseOrder;
import com.lis.reagent.service.ReagentPurchaseOrderService;
import com.lis.reagent.service.ReagentService;
import com.lis.reagent.service.SupplierService;
import com.lis.reagent.support.FileImportUtil;
import com.lis.reagent.vo.PriceCompareVO;
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
 * 采购订单 Controller（R-02 采购闭环）。
 *
 * <p>订单由已批准的采购申请转化（供应商必选），下单后经入库单验收推进状态。</p>
 *
 * @author LIS Team
 */
@RestController
@RequestMapping("/api/reagent/purchase-order")
@RequiredArgsConstructor
public class ReagentPurchaseOrderController {

    private final ReagentPurchaseOrderService orderService;
    private final SupplierService supplierService;
    private final ReagentService reagentService;

    /**
     * 下载采购订单明细导入模板（CSV）。
     */
    @GetMapping("/template")
    public void template(HttpServletResponse response) throws IOException {
        List<List<String>> sample = new ArrayList<>();
        sample.add(List.of("R26_GLU", "100", "mL", "35.00", "本月采购"));
        FileImportUtil.writeCsvTemplate(response, "purchase-order-template.csv",
                "试剂编码,数量,单位,单价,备注", sample);
    }

    /**
     * 解析采购订单明细导入文件，返回可填充到明细表格的行及行级错误。
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
        if (priceStr == null) {
            throw new IllegalArgumentException("单价不能为空");
        }

        Map<String, Object> item = new LinkedHashMap<>();
        item.put("reagentId", reagent.getId());
        item.put("reagentCode", reagent.getReagentCode());
        item.put("reagentName", reagent.getReagentName());
        item.put("specification", reagent.getSpecification());
        item.put("quantity", qty);
        item.put("unit", firstNonBlank(FileImportUtil.cell(cols, 2), reagent.getUnit()));
        item.put("unitPrice", new BigDecimal(priceStr));
        item.put("remark", FileImportUtil.cell(cols, 4));
        return item;
    }

    private String firstNonBlank(String a, String b) {
        if (a != null && !a.isEmpty()) return a;
        return b;
    }

    /** 分页查询 */
    @GetMapping("/page")
    public R<PageResult<ReagentPurchaseOrder>> page(@RequestParam(defaultValue = "1") long current,
                                                    @RequestParam(defaultValue = "10") long size,
                                                    @RequestParam(required = false) String orderNo,
                                                    @RequestParam(required = false) Integer status,
                                                    @RequestParam(required = false) Long applyId,
                                                    @RequestParam(required = false) Long supplierId) {
        Page<ReagentPurchaseOrder> page = orderService.queryPage(current, size, orderNo, status, applyId, supplierId);
        return R.ok(new PageResult<>(page.getRecords(), page.getTotal(), page.getCurrent(), page.getSize()));
    }

    /** 详情（含明细） */
    @GetMapping("/{id}")
    public R<ReagentPurchaseOrder> getDetail(@PathVariable Long id) {
        return R.ok(orderService.getDetail(id));
    }

    /** 由已批准申请创建订单 */
    @PostMapping
    public R<ReagentPurchaseOrder> createFromApply(@RequestBody OrderCreateDTO dto) {
        return R.ok(orderService.createFromApply(dto));
    }

    /** 下单（待下单 → 已下单） */
    @PostMapping("/{id}/place")
    public R<Void> place(@PathVariable Long id,
                         @RequestParam(required = false) Long operatorId,
                         @RequestParam(required = false) String operatorName) {
        orderService.placeOrder(id, new StockOpContext(operatorId, operatorName));
        return R.ok();
    }

    /** 取消订单（释放预算占用） */
    @PostMapping("/{id}/cancel")
    public R<Void> cancel(@PathVariable Long id,
                          @RequestParam(required = false) Long operatorId,
                          @RequestParam(required = false) String operatorName) {
        orderService.cancel(id, new StockOpContext(operatorId, operatorName));
        return R.ok();
    }

    /** 同试剂跨供应商历史比价（R-04，委托供应商服务） */
    @GetMapping("/compare")
    public R<PriceCompareVO> comparePrice(@RequestParam Long reagentId) {
        return R.ok(supplierService.comparePrice(reagentId));
    }
}
