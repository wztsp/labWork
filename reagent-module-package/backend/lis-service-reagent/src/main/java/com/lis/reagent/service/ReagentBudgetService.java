package com.lis.reagent.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.IService;
import com.lis.reagent.dto.BudgetQueryDTO;
import com.lis.reagent.dto.StockOpContext;
import com.lis.reagent.entity.ReagentBudget;
import com.lis.reagent.entity.ReagentPurchaseApply;
import com.lis.reagent.entity.ReagentPurchaseOrder;
import com.lis.reagent.support.BudgetGuard;
import com.lis.reagent.vo.BudgetReconcileVO;

import java.math.BigDecimal;

/**
 * 试剂预算服务（R-03，并发安全核心）。
 *
 * <p><b>并发方案（设计文档 §2.2.3，D + E 双保险）</b>：</p>
 * <ol>
 *   <li>占用 / 释放 / 实扣前先 {@code SELECT ... FOR UPDATE} 悲观行锁（方案 D），
 *       使「读 - 判 - 写」零窗口，同时拿到精确 before 快照写流水；</li>
 *   <li>UPDATE 一律带金额条件（CAS，方案 E），受影响行数必须为 1，否则抛异常回滚 ——
 *       即使判定逻辑有 bug，SQL 条件也不会让预算超支。</li>
 * </ol>
 *
 * <p><b>预算缺失红线</b>：{@link #freezeForApply} 前必须经
 * {@link BudgetGuard#requireBudgetExists} 校验，预算记录缺失抛
 * 「请先建立预算」异常，<b>绝不按 0 额度放行</b>。</p>
 *
 * @author LIS Team
 */
public interface ReagentBudgetService extends IService<ReagentBudget> {

    /**
     * 按「年度 + 科室 + 类别」匹配预算（不含锁，供展示 / 下拉）。
     *
     * <p>匹配顺序：先精确匹配类别 → 再降级匹配「不限类别」通用预算。</p>
     *
     * @param year       预算年度
     * @param deptId     科室 ID
     * @param categoryId 类别 ID，可为 null
     * @return 命中的预算；无匹配返回 null
     */
    ReagentBudget matchBudget(Integer year, Long deptId, Long categoryId);

    /**
     * 提交申请时冻结预算（占用）。
     *
     * <p>预算记录缺失抛 {@code BusinessException("预算记录不存在，请先为科室[...]建立[yyyy]年度预算")}；
     * 超额返回 {@link BudgetGuard.Verdict#EXCEED}（不抛异常，由调用方把申请置为「已冻结」）。</p>
     *
     * @param budgetId 预算 ID，不可为 null
     * @param amount   占用金额，必须为正数
     * @param applyId  来源申请单 ID
     * @param applyNo  来源申请单号
     * @param ctx      业务上下文
     * @return 判定结果：PASS / WARN 表示已成功冻结；EXCEED 表示未冻结
     */
    BudgetGuard.Verdict freezeForApply(Long budgetId, BigDecimal amount, Long applyId,
                                       String applyNo, StockOpContext ctx);

    /**
     * 驳回 / 作废 / 取消订单时释放占用。
     *
     * @param budgetId 预算 ID，可为 null（未冻结则跳过）
     * @param amount   释放金额，必须为正数
     * @param applyId  来源申请单 ID
     * @param applyNo  来源申请单号
     * @param ctx      业务上下文
     */
    void releaseForApply(Long budgetId, BigDecimal amount, Long applyId, String applyNo, StockOpContext ctx);

    /**
     * 判断某采购申请是否实际冻结过预算（存在 FREEZE 占用流水）。
     *
     * <p>EXCEED 提交时 {@link #freezeForApply} 判定不可通过会直接返回、不写 FREEZE 流水；
     * 此类申请取消 / 驳回时不应再调 {@link #releaseForApply}（否则 frozen=0 释放必然失败）。
     * 正常通过/预警冻结的申请该处恒返回 true。</p>
     *
     * @param budgetId 预算 ID，可为 null（未关联预算则视为未冻结）
     * @param applyId  申请单 ID，不可为 null
     * @return true 表示存在 FREEZE 流水，取消时应释放预算
     */
    boolean isFrozenForApply(Long budgetId, Long applyId);

    /**
     * 订单全部到货后实扣预算：释放冻结、计入实扣，写 SETTLE 流水并发布领域事件。
     *
     * <p>幂等：同一订单对同一预算只实扣一次（唯一索引 + 先查后写）。</p>
     *
     * @param order 采购订单，不可为 null
     * @param ctx   业务上下文
     */
    void settleForOrder(ReagentPurchaseOrder order, StockOpContext ctx);

    /**
     * 预算总额调增 / 调减（财务追加预算等）。
     *
     * @param budgetId   预算 ID，不可为 null
     * @param amount     调整金额，必须为正数
     * @param changeType 调整类型：4 调增 / 5 调减（{@code com.lis.reagent.enums.ChangeType}）
     * @param ctx        业务上下文
     */
    void adjust(Long budgetId, BigDecimal amount, Integer changeType, StockOpContext ctx);

    /**
     * 对账断言：流水推算值 与 主表余额 是否一致。
     *
     * @param budgetId 预算 ID，不可为 null
     * @return 对账结果 VO，恒不为 null
     */
    BudgetReconcileVO reconcile(Long budgetId);

    /**
     * 预算分页查询。
     *
     * @param dto 查询条件，不可为 null
     * @return 分页结果，恒不为 null
     */
    Page<ReagentBudget> queryPage(BudgetQueryDTO dto);

    /**
     * 创建预算（财务维护）。校验唯一维度（年度 + 科室 + 类别）。
     *
     * @param budget 预算实体，不可为 null
     * @return 创建后的预算（含自增主键）
     */
    ReagentBudget create(ReagentBudget budget);

    /**
     * 从申请单解冻重判（预算调增后重新判定并占用）。
     *
     * @param apply 申请单，须为「已冻结」状态，不可为 null
     * @param ctx   业务上下文
     * @return 判定结果：PASS / WARN 表示已成功冻结；EXCEED 表示仍超额
     */
    BudgetGuard.Verdict refreezeForApply(ReagentPurchaseApply apply, StockOpContext ctx);
}
