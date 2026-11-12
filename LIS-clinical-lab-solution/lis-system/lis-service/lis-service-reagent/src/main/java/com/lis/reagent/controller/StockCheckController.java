package com.lis.reagent.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.lis.common.core.result.PageResult;
import com.lis.common.core.result.R;
import com.lis.common.core.exception.BusinessException;
import com.lis.reagent.dto.ItemSubmitDTO;
import com.lis.reagent.dto.StockCheckCreateDTO;
import com.lis.reagent.dto.StockOpContext;
import com.lis.reagent.entity.Reagent;
import com.lis.reagent.entity.ReagentStockCheck;
import com.lis.reagent.entity.ReagentStockCheckItem;
import com.lis.reagent.mapper.ReagentMapper;
import com.lis.reagent.mapper.ReagentStockCheckItemMapper;
import com.lis.reagent.service.ReagentStockCheckService;
import com.lis.reagent.support.FileImportUtil;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 试剂库存盘点 Controller。
 *
 * <p><b>缺陷 P 修复</b>：调账逻辑已下沉到 {@link ReagentStockCheckService}，
 * 本类只做参数转发（create / submit / void），事务与幂等由 Service 保证。</p>
 *
 * @author LIS Team
 */
@Slf4j
@RestController
@RequestMapping("/api/reagent/stock-check")
@RequiredArgsConstructor
public class StockCheckController {

    private final ReagentStockCheckService checkService;
    private final ReagentStockCheckItemMapper itemMapper;
    private final ReagentMapper reagentMapper;

    /** 盘点单列表 */
    @GetMapping
    public R<PageResult<ReagentStockCheck>> page(@RequestParam(defaultValue = "1") long current,
                                                 @RequestParam(defaultValue = "10") long size,
                                                 @RequestParam(required = false) Long warehouseId,
                                                 @RequestParam(required = false) Integer status) {
        LambdaQueryWrapper<ReagentStockCheck> wrapper = new LambdaQueryWrapper<>();
        if (warehouseId != null) {
            wrapper.eq(ReagentStockCheck::getWarehouseId, warehouseId);
        }
        if (status != null) {
            wrapper.eq(ReagentStockCheck::getStatus, status);
        }
        wrapper.orderByDesc(ReagentStockCheck::getCreateTime);
        Page<ReagentStockCheck> page = checkService.page(new Page<>(current, size), wrapper);
        return R.ok(new PageResult<>(page.getRecords(), page.getTotal(), page.getCurrent(), page.getSize()));
    }

    /** 盘点单详情 */
    @GetMapping("/{id}")
    public R<ReagentStockCheck> getById(@PathVariable Long id) {
        return R.ok(checkService.getById(id));
    }

    /** 盘点明细列表 */
    @GetMapping("/{id}/items")
    public R<List<ReagentStockCheckItem>> listItems(@PathVariable Long id) {
        List<ReagentStockCheckItem> items = itemMapper.selectList(new LambdaQueryWrapper<ReagentStockCheckItem>()
                .eq(ReagentStockCheckItem::getCheckId, id));
        fillReagentNames(items);
        return R.ok(items);
    }

    /**
     * 批量带出试剂名称/编码（null 安全：试剂被删/不存在时字段为 null，前端已有兜底）。
     */
    private void fillReagentNames(List<ReagentStockCheckItem> items) {
        if (items == null || items.isEmpty()) {
            return;
        }
        Set<Long> reagentIds = items.stream()
                .map(ReagentStockCheckItem::getReagentId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        if (reagentIds.isEmpty()) {
            return;
        }
        Map<Long, Reagent> reagentMap = reagentMapper.selectBatchIds(reagentIds).stream()
                .collect(Collectors.toMap(Reagent::getId, Function.identity(), (a, b) -> a));
        for (ReagentStockCheckItem item : items) {
            Reagent reagent = reagentMap.get(item.getReagentId());
            if (reagent != null) {
                item.setReagentName(reagent.getReagentName());
                item.setReagentCode(reagent.getReagentCode());
            }
        }
    }

    /**
     * 创建盘点单（按仓库子树生成账面明细）。
     *
     * <p>body: {@code { "warehouseId": 1, "includeChildren": true, "operatorId": 1,
     * "operatorName": "张三", "remark": "月度盘点" }}；warehouseId 不传时盘点全库。</p>
     */
    @PostMapping
    public R<ReagentStockCheck> create(@RequestBody StockCheckCreateDTO dto) {
        return R.ok(checkService.create(dto));
    }

    /**
     * 录入实盘数量并调账，随后置为「已完成」。
     *
     * <p>body: {@code [ { "itemId": 明细ID, "actualQty": 实盘数量, "remark": "" }, ... ]}。
     * 差异非 0 自动生成盘盈 / 盘亏流水并回写库存；已完成单据重复提交被拒。</p>
     */
    @PostMapping("/{id}/submit")
    public R<Void> submitItems(@PathVariable Long id,
                               @RequestBody List<ItemSubmitDTO> items,
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
        checkService.submit(id, items, ctx);
        return R.ok();
    }

    /** 作废盘点单（仅进行中可作废） */
    @PostMapping("/{id}/void")
    public R<Void> voidCheck(@PathVariable Long id,
                             @RequestParam(required = false) String operatorName,
                             @RequestParam(required = false) String reason) {
        checkService.voidCheck(id, operatorName, reason);
        return R.ok();
    }

    /** 盘点审核（仅已提交/已完成的盘点单可审核） */
    @PostMapping("/{id}/audit")
    public R<Void> audit(@PathVariable Long id,
                         @RequestParam(required = false) Long operatorId,
                         @RequestParam(required = false) String operatorName) {
        checkService.audit(id, operatorId, operatorName);
        return R.ok();
    }

    /** 导出盘点明细 CSV（UTF-8 BOM） */
    @GetMapping("/{id}/export")
    public void export(@PathVariable Long id, HttpServletResponse response) throws IOException {
        ReagentStockCheck check = checkService.getById(id);
        if (check == null) {
            throw new BusinessException("盘点单不存在: " + id);
        }
        List<ReagentStockCheckItem> items = itemMapper.selectList(new LambdaQueryWrapper<ReagentStockCheckItem>()
                .eq(ReagentStockCheckItem::getCheckId, id)
                .orderByAsc(ReagentStockCheckItem::getId));
        fillReagentNames(items);
        List<List<String>> rows = new ArrayList<>();
        for (ReagentStockCheckItem item : items) {
            List<String> row = new ArrayList<>();
            row.add(nullToEmpty(item.getReagentCode()));
            row.add(nullToEmpty(item.getReagentName()));
            row.add(nullToEmpty(item.getLotNo()));
            row.add(qtyText(item.getBookQty()));
            row.add(qtyText(item.getActualQty()));
            row.add(qtyText(item.getDiffQty()));
            row.add(nullToEmpty(item.getDiffReason()));
            rows.add(row);
        }
        FileImportUtil.writeCsvTemplate(response, "stock-check-" + check.getCheckNo() + ".csv",
                "试剂编码,试剂名称,批次,账面数量,实盘数量,差异,差异原因", rows);
    }

    /** 数量转文本，null 输出空串。 */
    private String qtyText(BigDecimal qty) {
        return qty == null ? "" : qty.stripTrailingZeros().toPlainString();
    }

    /** 字符串转文本，null 输出空串。 */
    private String nullToEmpty(String s) {
        return s == null ? "" : s;
    }
}
