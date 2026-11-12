package com.lis.reagent.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.IService;
import com.lis.reagent.dto.ApplyCreateDTO;
import com.lis.reagent.dto.ApprovalDTO;
import com.lis.reagent.dto.StockOpContext;
import com.lis.reagent.entity.ReagentPurchaseApply;
import com.lis.reagent.vo.ApplyDetailVO;
import com.lis.reagent.vo.SubmitResultVO;

/**
 * 采购申请服务（R-02 核心）。
 *
 * <p><b>状态机</b>：{@code com.lis.reagent.support.PurchaseApplyStateMachine}
 * （0草稿 → 1待审批 → 2审批中 → 3已批准；4已驳回；5已冻结；6已作废）。</p>
 *
 * <p><b>预算红线</b>：提交时冻结预算，经 {@code BudgetGuard.requireBudgetExists}
 * 校验，预算记录缺失抛「请先建立预算」，<b>绝不按 0 放行</b>；超预算申请进入「已冻结」态，
 * 预算调增后可解冻重判。</p>
 *
 * <p><b>审批链</b>：由 {@code lis_reagent_approval_node} 配置表 + 纯 POJO
 * {@code ApprovalNodeResolver} 驱动（PURCHASE：科室主任恒触发；预算使用率达预警线追加分管院长）。</p>
 *
 * @author LIS Team
 */
public interface ReagentPurchaseApplyService extends IService<ReagentPurchaseApply> {

    /**
     * 草稿保存（创建或更新明细）。
     *
     * @param dto 申请单 + 明细
     * @return 保存后的申请单
     */
    ReagentPurchaseApply saveDraft(ApplyCreateDTO dto);

    /**
     * 提交申请：冻结预算 + 解析审批链。
     *
     * @param id  申请单 ID
     * @param ctx 业务上下文
     * @return 提交结果（含预算判定 / 审批链信息）
     */
    SubmitResultVO submit(Long id, StockOpContext ctx);

    /**
     * 审批通过。
     *
     * @param id  申请单 ID
     * @param dto 审批动作信息（action=1）
     */
    void approve(Long id, ApprovalDTO dto);

    /**
     * 审批驳回：释放预算占用。
     *
     * @param id  申请单 ID
     * @param dto 审批动作信息（action=2）
     */
    void reject(Long id, ApprovalDTO dto);

    /**
     * 取消申请：释放预算占用。
     *
     * @param id  申请单 ID
     * @param ctx 业务上下文
     */
    void cancel(Long id, StockOpContext ctx);

    /**
     * 解冻重判：预算调增后对「已冻结」申请重新判定并占用。
     *
     * @param id  申请单 ID
     * @param ctx 业务上下文
     * @return 提交结果；若仍超额则保持「已冻结」
     */
    SubmitResultVO unfreeze(Long id, StockOpContext ctx);

    /**
     * 详情（含明细 / 审批流水 / 预算信息 / 允许动作）。
     *
     * @param id 申请单 ID
     * @return 详情 VO，恒不为 null
     */
    ApplyDetailVO getDetail(Long id);

    /**
     * 分页查询。
     *
     * @param current    页码
     * @param size       页大小
     * @param status     状态，可为 null
     * @param applyNo    单号模糊，可为 null
     * @param deptId     科室，可为 null
     * @param categoryId 类别，可为 null
     * @return 分页结果
     */
    Page<ReagentPurchaseApply> queryPage(long current, long size, Integer status, String applyNo,
                                         Long deptId, Long categoryId);
}
