package com.lis.reagent.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.lis.common.core.exception.BusinessException;
import com.lis.reagent.dto.ApplyCreateDTO;
import com.lis.reagent.dto.ApprovalDTO;
import com.lis.reagent.dto.StockOpContext;
import com.lis.reagent.entity.Reagent;
import com.lis.reagent.entity.ReagentApprovalNode;
import com.lis.reagent.entity.ReagentBudget;
import com.lis.reagent.entity.ReagentPurchaseApply;
import com.lis.reagent.entity.ReagentPurchaseApplyItem;
import com.lis.reagent.entity.ReagentPurchaseApproval;
import com.lis.reagent.enums.ApplyStatus;
import com.lis.reagent.enums.ApprovalAction;
import com.lis.reagent.enums.BizType;
import com.lis.reagent.mapper.ReagentApprovalNodeMapper;
import com.lis.reagent.mapper.ReagentMapper;
import com.lis.reagent.mapper.ReagentPurchaseApplyItemMapper;
import com.lis.reagent.mapper.ReagentPurchaseApplyMapper;
import com.lis.reagent.mapper.ReagentPurchaseApprovalMapper;
import com.lis.reagent.service.ReagentBudgetService;
import com.lis.reagent.service.ReagentPurchaseApplyService;
import com.lis.reagent.support.ApprovalNodeResolver;
import com.lis.reagent.support.BizNoGenerator;
import com.lis.reagent.support.BudgetGuard;
import com.lis.reagent.support.PurchaseApplyStateMachine;
import com.lis.reagent.vo.ApplyDetailVO;
import com.lis.reagent.vo.SubmitResultVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * 采购申请服务实现（R-02 核心）。
 *
 * <p><b>一申请一预算</b>（设计文档 §2.2.4，死锁防范）：提交时校验明细类别收敛到
 * 同一个预算三元组，命中多条则要求拆单；全流程只对单行预算加锁，杜绝 ABBA 死锁。</p>
 *
 * @author LIS Team
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ReagentPurchaseApplyServiceImpl
        extends ServiceImpl<ReagentPurchaseApplyMapper, ReagentPurchaseApply>
        implements ReagentPurchaseApplyService {

    private final ReagentPurchaseApplyMapper applyMapper;
    private final ReagentPurchaseApplyItemMapper applyItemMapper;
    private final ReagentPurchaseApprovalMapper approvalMapper;
    private final ReagentApprovalNodeMapper approvalNodeMapper;
    private final ReagentMapper reagentMapper;
    private final ReagentBudgetService budgetService;

    /** 申请单号前缀（与 DDL 注释一致：PA + yyyyMMdd + 4位序列） */
    private static final String NO_PREFIX = "PA";

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ReagentPurchaseApply saveDraft(ApplyCreateDTO dto) {
        if (dto == null || dto.getItems() == null || dto.getItems().isEmpty()) {
            throw new BusinessException("申请明细不能为空");
        }
        if (dto.getApplyDeptId() == null) {
            throw new BusinessException("申请科室不能为空");
        }
        ReagentPurchaseApply apply;
        if (dto.getId() == null) {
            apply = new ReagentPurchaseApply();
            apply.setApplyNo(BizNoGenerator.nextNo(NO_PREFIX, applyMapper::selectMaxNo));
            apply.setStatus(ApplyStatus.DRAFT.getCode());
            apply.setCreateTime(LocalDateTime.now());
        } else {
            apply = applyMapper.selectByIdForUpdate(dto.getId());
            if (apply == null) {
                throw new BusinessException("申请单不存在: " + dto.getId());
            }
            // 仅草稿 / 已驳回可编辑（R-02 验收 2）
            PurchaseApplyStateMachine.assertEditable(ApplyStatus.of(apply.getStatus()));
            // 重建明细：先删旧
            applyItemMapper.delete(new LambdaQueryWrapper<ReagentPurchaseApplyItem>()
                    .eq(ReagentPurchaseApplyItem::getApplyId, apply.getId()));
        }
        apply.setApplyDeptId(dto.getApplyDeptId());
        apply.setApplyDeptName(dto.getApplyDeptName());
        apply.setApplyUserId(dto.getApplyUserId());
        apply.setApplyUserName(dto.getApplyUserName());
        apply.setBudgetYear(dto.getBudgetYear());
        apply.setUrgentFlag(dto.getUrgentFlag());
        apply.setExpectDate(dto.getExpectDate());
        apply.setRemark(dto.getRemark());
        apply.setUpdateTime(LocalDateTime.now());
        this.saveOrUpdate(apply);

        // 落明细（含试剂字典快照与行金额）
        BigDecimal total = BigDecimal.ZERO;
        List<ReagentPurchaseApplyItem> items = new ArrayList<>();
        for (ApplyCreateDTO.ApplyItemDTO itemDto : dto.getItems()) {
            if (itemDto.getReagentId() == null) {
                throw new BusinessException("明细缺少试剂");
            }
            if (itemDto.getQuantity() == null || itemDto.getQuantity().compareTo(BigDecimal.ZERO) <= 0) {
                throw new BusinessException("明细数量必须大于 0");
            }
            Reagent reagent = reagentMapper.selectById(itemDto.getReagentId());
            if (reagent == null) {
                throw new BusinessException("试剂不存在: " + itemDto.getReagentId());
            }
            BigDecimal price = itemDto.getEstimatedPrice() != null
                    ? itemDto.getEstimatedPrice()
                    : (reagent.getPricePurchase() != null ? reagent.getPricePurchase() : BigDecimal.ZERO);
            ReagentPurchaseApplyItem item = new ReagentPurchaseApplyItem();
            item.setApplyId(apply.getId());
            item.setReagentId(reagent.getId());
            item.setReagentCode(reagent.getReagentCode());
            item.setReagentName(reagent.getReagentName());
            item.setSpecification(reagent.getSpecification());
            item.setCategoryId(reagent.getCategoryId());
            item.setQuantity(itemDto.getQuantity());
            item.setUnit(itemDto.getUnit() != null ? itemDto.getUnit() : reagent.getUnit());
            item.setEstimatedPrice(price);
            item.setAmount(price.multiply(itemDto.getQuantity()));
            item.setRemark(itemDto.getRemark());
            item.setCreateTime(LocalDateTime.now());
            items.add(item);
            total = total.add(item.getAmount());
        }
        for (ReagentPurchaseApplyItem item : items) {
            applyItemMapper.insert(item);
        }
        apply.setTotalAmount(total);
        applyMapper.updateById(apply);
        log.info("保存采购申请草稿: applyNo={}, itemCount={}, total={}",
                apply.getApplyNo(), items.size(), total);
        return apply;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public SubmitResultVO submit(Long id, StockOpContext ctx) {
        ReagentPurchaseApply apply = applyMapper.selectByIdForUpdate(id);
        if (apply == null) {
            throw new BusinessException("申请单不存在: " + id);
        }
        ApplyStatus status = ApplyStatus.of(apply.getStatus());
        if (status == ApplyStatus.FROZEN) {
            throw new BusinessException("申请单处于冻结态，请先解冻再重新提交");
        }
        PurchaseApplyStateMachine.assertTransition(status,
                status == ApplyStatus.REJECTED ? ApprovalAction.RESUBMIT : ApprovalAction.SUBMIT);

        Integer year = apply.getBudgetYear() != null
                ? apply.getBudgetYear() : java.time.Year.now().getValue();
        // 一申请一预算：明细类别必须收敛（设计文档 §2.2.4）
        List<ReagentPurchaseApplyItem> items = applyItemMapper.selectList(
                new LambdaQueryWrapper<ReagentPurchaseApplyItem>()
                        .eq(ReagentPurchaseApplyItem::getApplyId, id));
        if (items.isEmpty()) {
            throw new BusinessException("申请明细不能为空");
        }
        Set<Long> categoryIds = new LinkedHashSet<>();
        for (ReagentPurchaseApplyItem item : items) {
            categoryIds.add(item.getCategoryId());
        }
        categoryIds.remove(null);
        if (categoryIds.size() > 1) {
            throw new BusinessException("本次申请涉及多个预算类别，请拆分为多张申请");
        }
        Long categoryId = categoryIds.isEmpty() ? null : categoryIds.iterator().next();

        // 匹配预算：精确类别 → 通用预算 → 缺失抛「请先建立预算」（绝不按 0 放行）
        ReagentBudget budget = budgetService.matchBudget(year, apply.getApplyDeptId(), categoryId);
        BudgetGuard.requireBudgetExists(budget, apply.getApplyDeptName(), year);
        apply.setBudgetId(budget.getId());
        apply.setBudgetYear(year);
        apply.setCategoryId(budget.getCategoryId());

        // 冻结预算：EXCEED 则进入「已冻结」态，不抛异常（供页面明示超额金额）
        BudgetGuard.Verdict verdict = budgetService.freezeForApply(budget.getId(),
                apply.getTotalAmount(), apply.getId(), apply.getApplyNo(), ctx);
        SubmitResultVO result = new SubmitResultVO();
        result.setApplyId(apply.getId());
        result.setApplyNo(apply.getApplyNo());
        result.setBudgetId(budget.getId());
        result.setTotalAmount(apply.getTotalAmount());
        result.setVerdict(verdict.getCode());
        result.setMessage(verdict.getLabel());
        if (!verdict.isPassable()) {
            apply.setStatus(ApplyStatus.FROZEN.getCode());
            apply.setOverBudgetFlag(1);
            apply.setExcessAmount(BudgetGuard.excessAmount(budget.getTotalAmount(),
                    budget.getFrozenAmount(), budget.getUsedAmount(), apply.getTotalAmount()));
            apply.setSubmitTime(LocalDateTime.now());
            apply.setUpdateTime(LocalDateTime.now());
            applyMapper.updateById(apply);
            result.setStatus(apply.getStatus());
            result.setStatusLabel(ApplyStatus.FROZEN.getLabel());
            result.setOverBudget(true);
            result.setExcessAmount(apply.getExcessAmount());
            return result;
        }

        // 解析审批链（WARN 时追加 OVER_BUDGET 节点，如分管院长）
        boolean overBudgetWarn = verdict == BudgetGuard.Verdict.WARN;
        List<ApprovalNodeResolver.ApprovalNodeSpec> chain = resolveChain(overBudgetWarn);
        apply.setCurrentNodeLevel(1);
        apply.setTotalNodeCount(chain.size());
        apply.setOverBudgetFlag(0);
        apply.setExcessAmount(null);
        apply.setStatus(ApplyStatus.PENDING.getCode());
        apply.setSubmitTime(LocalDateTime.now());
        apply.setUpdateTime(LocalDateTime.now());
        applyMapper.updateById(apply);

        result.setStatus(apply.getStatus());
        result.setStatusLabel(ApplyStatus.PENDING.getLabel());
        result.setOverBudget(false);
        result.setTotalNodeCount(chain.size());
        result.setCurrentNodeLevel(1);
        result.setCurrentNodeName(chain.isEmpty() ? "" : chain.get(0).getNodeName());
        return result;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void approve(Long id, ApprovalDTO dto) {
        ReagentPurchaseApply apply = applyMapper.selectByIdForUpdate(id);
        if (apply == null) {
            throw new BusinessException("申请单不存在: " + id);
        }
        ApplyStatus status = ApplyStatus.of(apply.getStatus());
        PurchaseApplyStateMachine.assertTransition(status, ApprovalAction.APPROVE);

        // 重新解析审批链定位当前节点名（totalNodeCount>1 说明提交时预算使用率达预警线）
        List<ApprovalNodeResolver.ApprovalNodeSpec> chain = resolveChain(apply.getTotalNodeCount() != null
                && apply.getTotalNodeCount() > 1);
        ApprovalNodeResolver.ApprovalNodeSpec node = ApprovalNodeResolver.nodeAt(chain,
                apply.getCurrentNodeLevel() == null ? 1 : apply.getCurrentNodeLevel());
        writeApproval(apply, node, ApprovalAction.APPROVE, dto);

        boolean isLast = ApprovalNodeResolver.isLastNode(
                apply.getCurrentNodeLevel() == null ? 1 : apply.getCurrentNodeLevel(),
                apply.getTotalNodeCount() == null ? 0 : apply.getTotalNodeCount());
        ApplyStatus next = PurchaseApplyStateMachine.next(status, ApprovalAction.APPROVE, isLast);
        apply.setStatus(next.getCode());
        if (next == ApplyStatus.APPROVED) {
            apply.setFinishTime(LocalDateTime.now());
        } else {
            apply.setCurrentNodeLevel((apply.getCurrentNodeLevel() == null ? 1 : apply.getCurrentNodeLevel()) + 1);
        }
        apply.setUpdateTime(LocalDateTime.now());
        applyMapper.updateById(apply);
        log.info("采购申请审批通过: applyNo={}, next={}", apply.getApplyNo(), next.getLabel());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void reject(Long id, ApprovalDTO dto) {
        ReagentPurchaseApply apply = applyMapper.selectByIdForUpdate(id);
        if (apply == null) {
            throw new BusinessException("申请单不存在: " + id);
        }
        ApplyStatus status = ApplyStatus.of(apply.getStatus());
        PurchaseApplyStateMachine.assertTransition(status, ApprovalAction.REJECT);

        List<ApprovalNodeResolver.ApprovalNodeSpec> chain = resolveChain(apply.getTotalNodeCount() != null
                && apply.getTotalNodeCount() > 1);
        ApprovalNodeResolver.ApprovalNodeSpec node = ApprovalNodeResolver.nodeAt(chain,
                apply.getCurrentNodeLevel() == null ? 1 : apply.getCurrentNodeLevel());
        writeApproval(apply, node, ApprovalAction.REJECT, dto);

        apply.setStatus(ApplyStatus.REJECTED.getCode());
        apply.setUpdateTime(LocalDateTime.now());
        applyMapper.updateById(apply);
        // 驳回释放预算占用（R-02 验收：驳回释放预算）
        budgetService.releaseForApply(apply.getBudgetId(), apply.getTotalAmount(),
                apply.getId(), apply.getApplyNo(), dtoToCtx(dto));
        log.info("采购申请审批驳回: applyNo={}", apply.getApplyNo());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void cancel(Long id, StockOpContext ctx) {
        ReagentPurchaseApply apply = applyMapper.selectByIdForUpdate(id);
        if (apply == null) {
            throw new BusinessException("申请单不存在: " + id);
        }
        ApplyStatus status = ApplyStatus.of(apply.getStatus());
        if (status == null || status.isTerminal()) {
            throw new BusinessException("当前状态不允许作废: "
                    + (status == null ? apply.getStatus() : status.getLabel()));
        }
        PurchaseApplyStateMachine.assertTransition(status, ApprovalAction.CANCEL);
        apply.setStatus(ApplyStatus.CANCELLED.getCode());
        apply.setUpdateTime(LocalDateTime.now());
        applyMapper.updateById(apply);
        // 作废释放预算占用：仅实际冻结过预算的申请才释放
        // （EXCEED 提交时未冻结、无 FREEZE 流水，直接作废即可，避免 frozen=0 释放失败）
        if (budgetService.isFrozenForApply(apply.getBudgetId(), apply.getId())) {
            budgetService.releaseForApply(apply.getBudgetId(), apply.getTotalAmount(),
                    apply.getId(), apply.getApplyNo(), ctx);
        }
        log.info("采购申请作废: applyNo={}", apply.getApplyNo());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public SubmitResultVO unfreeze(Long id, StockOpContext ctx) {
        ReagentPurchaseApply apply = applyMapper.selectByIdForUpdate(id);
        if (apply == null) {
            throw new BusinessException("申请单不存在: " + id);
        }
        ApplyStatus status = ApplyStatus.of(apply.getStatus());
        PurchaseApplyStateMachine.assertTransition(status, ApprovalAction.UNFREEZE);

        // 预算调增后重新判定并占用
        BudgetGuard.Verdict verdict = budgetService.refreezeForApply(apply, ctx);
        SubmitResultVO result = new SubmitResultVO();
        result.setApplyId(apply.getId());
        result.setApplyNo(apply.getApplyNo());
        result.setBudgetId(apply.getBudgetId());
        result.setTotalAmount(apply.getTotalAmount());
        result.setVerdict(verdict.getCode());
        result.setMessage(verdict.getLabel());
        if (!verdict.isPassable()) {
            ReagentBudget budget = budgetService.getById(apply.getBudgetId());
            apply.setExcessAmount(BudgetGuard.excessAmount(
                    budget == null ? BigDecimal.ZERO : budget.getTotalAmount(),
                    budget == null ? BigDecimal.ZERO : budget.getFrozenAmount(),
                    budget == null ? BigDecimal.ZERO : budget.getUsedAmount(),
                    apply.getTotalAmount()));
            apply.setUpdateTime(LocalDateTime.now());
            applyMapper.updateById(apply);
            result.setStatus(apply.getStatus());
            result.setStatusLabel(ApplyStatus.FROZEN.getLabel());
            result.setOverBudget(true);
            result.setExcessAmount(apply.getExcessAmount());
            return result;
        }
        // 解冻成功：回到待审批，重新解析审批链
        boolean overBudgetWarn = verdict == BudgetGuard.Verdict.WARN;
        List<ApprovalNodeResolver.ApprovalNodeSpec> chain = resolveChain(overBudgetWarn);
        apply.setStatus(ApplyStatus.PENDING.getCode());
        apply.setCurrentNodeLevel(1);
        apply.setTotalNodeCount(chain.size());
        apply.setOverBudgetFlag(0);
        apply.setExcessAmount(null);
        apply.setSubmitTime(LocalDateTime.now());
        apply.setUpdateTime(LocalDateTime.now());
        applyMapper.updateById(apply);
        result.setStatus(apply.getStatus());
        result.setStatusLabel(ApplyStatus.PENDING.getLabel());
        result.setOverBudget(false);
        result.setTotalNodeCount(chain.size());
        result.setCurrentNodeLevel(1);
        result.setCurrentNodeName(chain.isEmpty() ? "" : chain.get(0).getNodeName());
        return result;
    }

    @Override
    public ApplyDetailVO getDetail(Long id) {
        ReagentPurchaseApply apply = applyMapper.selectById(id);
        if (apply == null) {
            throw new BusinessException("申请单不存在: " + id);
        }
        List<ReagentPurchaseApplyItem> items = applyItemMapper.selectList(
                new LambdaQueryWrapper<ReagentPurchaseApplyItem>()
                        .eq(ReagentPurchaseApplyItem::getApplyId, id)
                        .orderByAsc(ReagentPurchaseApplyItem::getId));
        List<ReagentPurchaseApproval> approvals = approvalMapper.selectList(
                new LambdaQueryWrapper<ReagentPurchaseApproval>()
                        .eq(ReagentPurchaseApproval::getApplyId, id)
                        .orderByAsc(ReagentPurchaseApproval::getRoundNo)
                        .orderByAsc(ReagentPurchaseApproval::getNodeLevel));

        ApplyDetailVO vo = new ApplyDetailVO();
        vo.setApply(apply);
        vo.setItems(items);
        vo.setApprovals(approvals);
        ApplyStatus status = ApplyStatus.of(apply.getStatus());
        vo.setEditable(PurchaseApplyStateMachine.isEditable(status));
        Set<ApprovalAction> actions = PurchaseApplyStateMachine.allowedActions(status);
        List<String> actionLabels = new ArrayList<>();
        for (ApprovalAction a : actions) {
            actionLabels.add(a.getRecordCode() + ":" + a.getLabel());
        }
        vo.setAllowedActions(actionLabels);
        // 当前节点名
        if (apply.getTotalNodeCount() != null && apply.getTotalNodeCount() > 0) {
            List<ApprovalNodeResolver.ApprovalNodeSpec> chain = resolveChain(apply.getTotalNodeCount() > 1);
            ApprovalNodeResolver.ApprovalNodeSpec node = ApprovalNodeResolver.nodeAt(chain,
                    apply.getCurrentNodeLevel() == null ? 1 : apply.getCurrentNodeLevel());
            vo.setCurrentNodeName(node == null ? "" : node.getNodeName());
        }
        // 预算信息
        if (apply.getBudgetId() != null) {
            ReagentBudget budget = budgetService.getById(apply.getBudgetId());
            if (budget != null) {
                vo.setBudgetTotalAmount(budget.getTotalAmount());
                vo.setBudgetFrozenAmount(budget.getFrozenAmount());
                vo.setBudgetUsedAmount(budget.getUsedAmount());
                vo.setBudgetAvailable(BudgetGuard.available(budget.getTotalAmount(),
                        budget.getFrozenAmount(), budget.getUsedAmount()));
            }
        }
        return vo;
    }

    @Override
    public Page<ReagentPurchaseApply> queryPage(long current, long size, Integer status, String applyNo,
                                                Long deptId, Long categoryId) {
        LambdaQueryWrapper<ReagentPurchaseApply> w = new LambdaQueryWrapper<>();
        if (status != null) {
            w.eq(ReagentPurchaseApply::getStatus, status);
        }
        if (applyNo != null && !applyNo.trim().isEmpty()) {
            w.like(ReagentPurchaseApply::getApplyNo, applyNo.trim());
        }
        if (deptId != null) {
            w.eq(ReagentPurchaseApply::getApplyDeptId, deptId);
        }
        if (categoryId != null) {
            w.eq(ReagentPurchaseApply::getCategoryId, categoryId);
        }
        w.orderByDesc(ReagentPurchaseApply::getCreateTime);
        return this.page(new Page<>(current, size), w);
    }

    /**
     * 解析采购审批链：加载配置表全部启用节点，按上下文过滤并重新编号。
     */
    private List<ApprovalNodeResolver.ApprovalNodeSpec> resolveChain(boolean overBudgetWarn) {
        List<ReagentApprovalNode> nodes = approvalNodeMapper.selectEnabledByBizType(BizType.PURCHASE.getCode());
        if (nodes == null || nodes.isEmpty()) {
            return Collections.emptyList();
        }
        List<ApprovalNodeResolver.ApprovalNodeSpec> specs = new ArrayList<>();
        for (ReagentApprovalNode n : nodes) {
            specs.add(new ApprovalNodeResolver.ApprovalNodeSpec(
                    n.getId(), n.getNodeLevel(), n.getNodeName(),
                    ApprovalNodeResolver.TriggerCond.of(n.getTriggerCond()),
                    n.getApproverRoleCode(),
                    n.getApproverDeptScope() == null ? "1" : String.valueOf(n.getApproverDeptScope())));
        }
        return ApprovalNodeResolver.resolve(specs,
                new ApprovalNodeResolver.ApprovalContext(overBudgetWarn, false));
    }

    /**
     * 写审批留痕（只增不改不删）。
     */
    private void writeApproval(ReagentPurchaseApply apply,
                               ApprovalNodeResolver.ApprovalNodeSpec node,
                               ApprovalAction action, ApprovalDTO dto) {
        int round = approvalMapper.maxRoundNo(apply.getId()) + 1;
        ReagentPurchaseApproval approval = new ReagentPurchaseApproval();
        approval.setApplyId(apply.getId());
        approval.setApplyNo(apply.getApplyNo());
        approval.setNodeLevel(apply.getCurrentNodeLevel() == null ? 1 : apply.getCurrentNodeLevel());
        approval.setNodeName(node == null ? (action == ApprovalAction.APPROVE ? "审批通过" : "审批驳回")
                : node.getNodeName());
        approval.setAction(action.getRecordCode());
        approval.setApproverId(dto == null ? null : dto.getApproverId());
        approval.setApproverName(dto == null ? null : dto.getApproverName());
        approval.setOpinion(dto == null ? null : dto.getOpinion());
        approval.setApproveTime(LocalDateTime.now());
        approval.setRoundNo(round);
        approval.setCreateTime(LocalDateTime.now());
        approvalMapper.insert(approval);
    }

    private StockOpContext dtoToCtx(ApprovalDTO dto) {
        return StockOpContext.builder()
                .operatorId(dto == null ? null : dto.getApproverId())
                .operatorName(dto == null ? null : dto.getApproverName())
                .build();
    }
}
