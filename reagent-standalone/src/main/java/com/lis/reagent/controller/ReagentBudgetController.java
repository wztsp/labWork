package com.lis.reagent.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.lis.common.core.result.PageResult;
import com.lis.common.core.result.R;
import com.lis.reagent.dto.BudgetQueryDTO;
import com.lis.reagent.dto.StockOpContext;
import com.lis.reagent.entity.ReagentBudget;
import com.lis.reagent.enums.ChangeType;
import com.lis.reagent.service.ReagentBudgetService;
import com.lis.reagent.support.BudgetGuard;
import com.lis.reagent.vo.BudgetReconcileVO;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;

/**
 * 试剂预算 Controller（R-03）。
 *
 * <p>预算余额是强一致数据，<b>禁止使用缓存</b>（设计文档 §2.2.7），
 * 所有查询直接读库。写操作（创建 / 调增调减）走 Service 事务。</p>
 *
 * @author LIS Team
 */
@RestController
@RequestMapping("/api/reagent/budget")
@RequiredArgsConstructor
public class ReagentBudgetController {

    private final ReagentBudgetService budgetService;

    /** 分页查询预算 */
    @GetMapping("/page")
    public R<PageResult<ReagentBudget>> page(BudgetQueryDTO dto) {
        Page<ReagentBudget> page = budgetService.queryPage(dto);
        return R.ok(new PageResult<>(page.getRecords(), page.getTotal(), page.getCurrent(), page.getSize()));
    }

    /** 预算详情 */
    @GetMapping("/{id}")
    public R<ReagentBudget> getById(@PathVariable Long id) {
        return R.ok(budgetService.getById(id));
    }

    /** 对账断言：流水推算值 与 主表余额 是否一致（QA 验收可直接调用） */
    @GetMapping("/{id}/reconcile")
    public R<BudgetReconcileVO> reconcile(@PathVariable Long id) {
        return R.ok(budgetService.reconcile(id));
    }

    /** 按「年度+科室+类别」匹配预算（供提交申请前选择，不锁行） */
    @GetMapping("/match")
    public R<ReagentBudget> match(@RequestParam Integer year,
                                  @RequestParam Long deptId,
                                  @RequestParam(required = false) Long categoryId) {
        return R.ok(budgetService.matchBudget(year, deptId, categoryId));
    }

    /** 创建预算（财务维护） */
    @PostMapping
    public R<ReagentBudget> create(@RequestBody ReagentBudget budget) {
        return R.ok(budgetService.create(budget));
    }

    /**
     * 预算总额调增 / 调减。
     *
     * @param id         预算 ID
     * @param changeType 调整类型：4 调增 / 5 调减
     * @param amount     调整金额（正数）
     */
    @PostMapping("/{id}/adjust")
    public R<Void> adjust(@PathVariable Long id,
                          @RequestParam Integer changeType,
                          @RequestParam BigDecimal amount,
                          @RequestParam(required = false) Long operatorId,
                          @RequestParam(required = false) String operatorName) {
        StockOpContext ctx = new StockOpContext(operatorId, operatorName);
        budgetService.adjust(id, amount, changeType, ctx);
        return R.ok();
    }

    /** 判定：拟占用金额是否会超预算（只读，供前端提示） */
    @GetMapping("/{id}/judge")
    public R<BudgetGuard.Verdict> judge(@PathVariable Long id,
                                        @RequestParam BigDecimal amount) {
        ReagentBudget b = budgetService.getById(id);
        if (b == null) {
            return R.fail("预算不存在: " + id);
        }
        return R.ok(BudgetGuard.judge(b.getTotalAmount(), b.getFrozenAmount(),
                b.getUsedAmount(), amount, b.getWarnRatio()));
    }
}
