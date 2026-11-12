package com.lis.reagent.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.lis.common.core.exception.BusinessException;
import com.lis.reagent.dto.BudgetQueryDTO;
import com.lis.reagent.dto.BudgetSettledEvent;
import com.lis.reagent.dto.StockOpContext;
import com.lis.reagent.entity.ReagentBudget;
import com.lis.reagent.entity.ReagentBudgetRecord;
import com.lis.reagent.entity.ReagentPurchaseApply;
import com.lis.reagent.entity.ReagentPurchaseOrder;
import com.lis.reagent.enums.ChangeType;
import com.lis.reagent.enums.SourceType;
import com.lis.reagent.mapper.ReagentBudgetMapper;
import com.lis.reagent.mapper.ReagentBudgetRecordMapper;
import com.lis.reagent.mapper.ReagentPurchaseApplyMapper;
import com.lis.reagent.service.ReagentBudgetService;
import com.lis.reagent.support.BudgetGuard;
import com.lis.reagent.vo.BudgetReconcileVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 预算服务实现。
 *
 * <p><b>事务边界</b>：每个业务动作（冻结 / 释放 / 实扣 / 调增 / 调减）一个事务，
 * 事务内先悲观行锁再判定再写入，流水与主表同事务 —— 这是对账断言恒成立的保证。</p>
 *
 * @author LIS Team
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ReagentBudgetServiceImpl extends ServiceImpl<ReagentBudgetMapper, ReagentBudget>
        implements ReagentBudgetService {

    private final ReagentBudgetMapper budgetMapper;
    private final ReagentBudgetRecordMapper budgetRecordMapper;
    private final ReagentPurchaseApplyMapper applyMapper;
    private final ApplicationEventPublisher eventPublisher;

    /**
     * MANUAL 人工调整流水的来源序号：进程内自增（毫秒时间戳打底）。
     *
     * <p>{@code lis_reagent_budget_record.source_id} 为 BIGINT，唯一索引
     * {@code uk_lis_reagent_budget_record_src (budget_id, change_type, source_type, source_id)}
     * 对 MANUAL 类流水若沿用预算自身 id 作为 source_id，同一预算第二次调增/调减必撞约束。
     * 人工调整是独立业务动作、无幂等语义，故每次生成唯一 Long 序号，确保可多次调增/调减；
     * 进程重启后时间戳前移，不会与历史值重复。</p>
     */
    private static final AtomicLong MANUAL_SOURCE_SEQ = new AtomicLong(System.currentTimeMillis());

    @Override
    public ReagentBudget matchBudget(Integer year, Long deptId, Long categoryId) {
        if (year == null || deptId == null) {
            return null;
        }
        // 先精确匹配类别
        ReagentBudget exact = budgetMapper.selectOne(new LambdaQueryWrapper<ReagentBudget>()
                .eq(ReagentBudget::getBudgetYear, year)
                .eq(ReagentBudget::getDeptId, deptId)
                .eq(ReagentBudget::getCategoryId, categoryId)
                .eq(ReagentBudget::getStatus, 1)
                .last("LIMIT 1"));
        if (exact != null) {
            return exact;
        }
        // 降级匹配「不限类别」通用预算
        return budgetMapper.selectOne(new LambdaQueryWrapper<ReagentBudget>()
                .eq(ReagentBudget::getBudgetYear, year)
                .eq(ReagentBudget::getDeptId, deptId)
                .isNull(ReagentBudget::getCategoryId)
                .eq(ReagentBudget::getStatus, 1)
                .last("LIMIT 1"));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public BudgetGuard.Verdict freezeForApply(Long budgetId, BigDecimal amount, Long applyId,
                                              String applyNo, StockOpContext ctx) {
        BudgetGuard.requirePositive(amount, "占用金额");
        ReagentBudget b = budgetMapper.selectByIdForUpdate(budgetId);
        // T-08 红线：预算记录缺失 -> 抛「请先建立预算」，绝不按 0 额度静默处理
        BudgetGuard.requireBudgetExists(b,
                b == null ? null : b.getDeptName(),
                b == null ? null : b.getBudgetYear());
        BudgetGuard.Verdict verdict = BudgetGuard.judge(b.getTotalAmount(), b.getFrozenAmount(),
                b.getUsedAmount(), amount, b.getWarnRatio());
        if (!verdict.isPassable()) {
            return verdict;
        }
        int rows = budgetMapper.tryFreeze(budgetId, amount);
        if (rows != 1) {
            // 走到这里说明 BudgetGuard 判定与 SQL 条件不一致 —— 代码 bug，绝不能静默放过
            throw new BusinessException("预算占用失败(并发或超额)，budgetId=" + budgetId);
        }
        BigDecimal beforeFrozen = nz(b.getFrozenAmount());
        BigDecimal beforeUsed = nz(b.getUsedAmount());
        writeRecord(budgetId, ChangeType.FREEZE.getCode(), amount,
                beforeFrozen, beforeFrozen.add(amount),
                beforeUsed, beforeUsed,
                SourceType.APPLY.getCode(), applyId, applyNo, ctx);
        return verdict;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void releaseForApply(Long budgetId, BigDecimal amount, Long applyId,
                                String applyNo, StockOpContext ctx) {
        if (budgetId == null || amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            return; // 未冻结 / 金额无效，无需释放
        }
        ReagentBudget b = budgetMapper.selectByIdForUpdate(budgetId);
        if (b == null) {
            return;
        }
        int rows = budgetMapper.tryRelease(budgetId, amount);
        if (rows != 1) {
            throw new BusinessException("预算释放失败(frozen不足或并发)，budgetId=" + budgetId);
        }
        BigDecimal beforeFrozen = nz(b.getFrozenAmount());
        BigDecimal beforeUsed = nz(b.getUsedAmount());
        writeRecord(budgetId, ChangeType.RELEASE.getCode(), amount,
                beforeFrozen, beforeFrozen.subtract(amount),
                beforeUsed, beforeUsed,
                SourceType.APPLY.getCode(), applyId, applyNo, ctx);
    }

    @Override
    public boolean isFrozenForApply(Long budgetId, Long applyId) {
        if (budgetId == null || applyId == null) {
            return false;
        }
        // 存在 FREEZE（占用）流水即视为实际冻结；EXCEED 提交未冻结时无此流水
        return budgetRecordMapper.countBySource(budgetId, ChangeType.FREEZE.getCode(),
                SourceType.APPLY.getCode(), applyId) > 0;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void settleForOrder(ReagentPurchaseOrder order, StockOpContext ctx) {
        if (order == null || order.getApplyId() == null) {
            return;
        }
        ReagentPurchaseApply apply = applyMapper.selectById(order.getApplyId());
        if (apply == null || apply.getBudgetId() == null) {
            log.warn("订单无关联预算，跳过实扣: orderId={}, applyId={}", order.getId(), order.getApplyId());
            return;
        }
        Long budgetId = apply.getBudgetId();
        BigDecimal freezeAmount = nz(apply.getTotalAmount());
        BigDecimal usedAmount = nz(order.getTotalAmount());
        if (freezeAmount.compareTo(BigDecimal.ZERO) <= 0 && usedAmount.compareTo(BigDecimal.ZERO) <= 0) {
            return;
        }
        // 幂等：同一订单对同一预算只实扣一次（唯一索引兜底）
        if (budgetRecordMapper.countBySource(budgetId, ChangeType.SETTLE.getCode(),
                SourceType.ORDER.getCode(), order.getId()) > 0) {
            return;
        }
        ReagentBudget b = budgetMapper.selectByIdForUpdate(budgetId);
        if (b == null) {
            throw new BusinessException("预算不存在: " + budgetId);
        }
        int rows = budgetMapper.trySettle(budgetId, freezeAmount, usedAmount);
        if (rows != 1) {
            throw new BusinessException("预算实扣失败(frozen不足或并发)，budgetId=" + budgetId);
        }
        BigDecimal beforeFrozen = nz(b.getFrozenAmount());
        BigDecimal beforeUsed = nz(b.getUsedAmount());
        writeRecord(budgetId, ChangeType.SETTLE.getCode(), usedAmount,
                beforeFrozen, beforeFrozen.subtract(freezeAmount),
                beforeUsed, beforeUsed.add(usedAmount),
                SourceType.ORDER.getCode(), order.getId(), order.getOrderNo(), ctx);
        // 预留扩展点：实扣成功后发布领域事件（R-15，P2 对接用），本轮无订阅者
        eventPublisher.publishEvent(new BudgetSettledEvent(
                budgetId, order.getId(), order.getOrderNo(), usedAmount,
                order.getDeptId(), b.getBudgetYear(), LocalDateTime.now()));
        log.info("预算实扣完成: budgetId={}, orderNo={}, used={}, freezeReleased={}",
                budgetId, order.getOrderNo(), usedAmount, freezeAmount);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void adjust(Long budgetId, BigDecimal amount, Integer changeType, StockOpContext ctx) {
        BudgetGuard.requirePositive(amount, "调整金额");
        ReagentBudget b = budgetMapper.selectByIdForUpdate(budgetId);
        BudgetGuard.requireBudgetExists(b,
                b == null ? null : b.getDeptName(),
                b == null ? null : b.getBudgetYear());
        int rows;
        Integer effectiveType;
        if (ChangeType.INCREASE.getCode() == changeType) {
            rows = budgetMapper.tryIncrease(budgetId, amount);
            effectiveType = ChangeType.INCREASE.getCode();
        } else if (ChangeType.DECREASE.getCode() == changeType) {
            rows = budgetMapper.tryDecrease(budgetId, amount);
            effectiveType = ChangeType.DECREASE.getCode();
        } else {
            throw new BusinessException("不支持的预算调整类型: " + changeType);
        }
        if (rows != 1) {
            throw new BusinessException("预算调整失败(调减不得低于已用额度或并发)，budgetId=" + budgetId);
        }
        // 流水记录调整金额；总额变动不在 frozen/used 快照范围内，前后值保持原样
        BigDecimal beforeFrozen = nz(b.getFrozenAmount());
        BigDecimal beforeUsed = nz(b.getUsedAmount());
        writeRecord(budgetId, effectiveType, amount,
                beforeFrozen, beforeFrozen, beforeUsed, beforeUsed,
                SourceType.MANUAL.getCode(), MANUAL_SOURCE_SEQ.incrementAndGet(), b.getBudgetNo(), ctx);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public BudgetGuard.Verdict refreezeForApply(ReagentPurchaseApply apply, StockOpContext ctx) {
        if (apply == null || apply.getBudgetId() == null) {
            throw new BusinessException("申请单未关联预算，无法解冻重判");
        }
        return freezeForApply(apply.getBudgetId(), nz(apply.getTotalAmount()),
                apply.getId(), apply.getApplyNo(), ctx);
    }

    @Override
    public BudgetReconcileVO reconcile(Long budgetId) {
        ReagentBudget b = budgetMapper.selectById(budgetId);
        if (b == null) {
            throw new BusinessException("预算不存在: " + budgetId);
        }
        BigDecimal masterFrozen = nz(b.getFrozenAmount());
        BigDecimal masterUsed = nz(b.getUsedAmount());
        BigDecimal calcFrozen = nz(budgetRecordMapper.calcFrozenFromRecords(budgetId));
        BigDecimal calcUsed = nz(budgetRecordMapper.calcUsedFromRecords(budgetId));
        boolean consistent = masterFrozen.compareTo(calcFrozen) == 0
                && masterUsed.compareTo(calcUsed) == 0;

        BudgetReconcileVO vo = new BudgetReconcileVO();
        vo.setBudgetId(budgetId);
        vo.setBudgetNo(b.getBudgetNo());
        vo.setMasterFrozen(masterFrozen);
        vo.setMasterUsed(masterUsed);
        vo.setRecordFrozen(calcFrozen);
        vo.setRecordUsed(calcUsed);
        vo.setFrozenDiff(masterFrozen.subtract(calcFrozen));
        vo.setUsedDiff(masterUsed.subtract(calcUsed));
        vo.setConsistent(consistent);
        vo.setRecordCount(budgetRecordMapper.countByBudget(budgetId));
        vo.setMessage(consistent ? "对账一致" : "对账不一致，请排查流水");
        return vo;
    }

    @Override
    public Page<ReagentBudget> queryPage(BudgetQueryDTO dto) {
        LambdaQueryWrapper<ReagentBudget> w = new LambdaQueryWrapper<>();
        if (dto.getBudgetYear() != null) {
            w.eq(ReagentBudget::getBudgetYear, dto.getBudgetYear());
        }
        if (dto.getDeptId() != null) {
            w.eq(ReagentBudget::getDeptId, dto.getDeptId());
        }
        if (dto.getCategoryId() != null) {
            w.eq(ReagentBudget::getCategoryId, dto.getCategoryId());
        }
        if (dto.getStatus() != null) {
            w.eq(ReagentBudget::getStatus, dto.getStatus());
        }
        if (dto.getBudgetNo() != null && !dto.getBudgetNo().trim().isEmpty()) {
            w.like(ReagentBudget::getBudgetNo, dto.getBudgetNo().trim());
        }
        if (Boolean.TRUE.equals(dto.getOnlyWarning())) {
            // 常量条件串，无用户输入拼接，不构成注入面
            w.apply("frozen_amount + used_amount >= total_amount * COALESCE(warn_ratio, 0.8)");
        }
        w.orderByDesc(ReagentBudget::getBudgetYear).orderByAsc(ReagentBudget::getDeptId)
                .orderByAsc(ReagentBudget::getId);
        Page<ReagentBudget> page = new Page<>(dto.getPageNum() == null ? 1 : dto.getPageNum(),
                dto.getPageSize() == null ? 10 : dto.getPageSize());
        return this.page(page, w);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ReagentBudget create(ReagentBudget budget) {
        if (budget == null || budget.getBudgetYear() == null || budget.getDeptId() == null) {
            throw new BusinessException("预算年度与科室不能为空");
        }
        if (budget.getTotalAmount() == null || budget.getTotalAmount().compareTo(BigDecimal.ZERO) < 0) {
            throw new BusinessException("预算总额不能为负数");
        }
        // 唯一维度（年度 + 科室 + 类别，类别 NULL 视为通用预算）
        Long existed = this.count(new LambdaQueryWrapper<ReagentBudget>()
                .eq(ReagentBudget::getBudgetYear, budget.getBudgetYear())
                .eq(ReagentBudget::getDeptId, budget.getDeptId())
                .eq(ReagentBudget::getCategoryId, budget.getCategoryId()));
        if (existed > 0) {
            throw new BusinessException("该科室该年度" + (budget.getCategoryId() == null ? "通用" : "该类别")
                    + "预算已存在，请勿重复创建");
        }
        if (budget.getBudgetNo() == null || budget.getBudgetNo().trim().isEmpty()) {
            budget.setBudgetNo("BUD" + budget.getBudgetYear() + "-" + budget.getDeptId());
        }
        budget.setFrozenAmount(nz(budget.getFrozenAmount()));
        budget.setUsedAmount(nz(budget.getUsedAmount()));
        if (budget.getWarnRatio() == null) {
            budget.setWarnRatio(BudgetGuard.DEFAULT_WARN_RATIO);
        }
        if (budget.getStatus() == null) {
            budget.setStatus(1);
        }
        if (budget.getVersion() == null) {
            budget.setVersion(0);
        }
        budget.setCreateTime(LocalDateTime.now());
        budget.setUpdateTime(LocalDateTime.now());
        this.save(budget);
        return budget;
    }

    /**
     * 统一流水写入点：预算变动必须同事务写流水，且带 before/after 快照支撑对账断言。
     */
    private void writeRecord(Long budgetId, Integer changeType, BigDecimal amount,
                             BigDecimal beforeFrozen, BigDecimal afterFrozen,
                             BigDecimal beforeUsed, BigDecimal afterUsed,
                             String sourceType, Long sourceId, String sourceNo, StockOpContext ctx) {
        ReagentBudgetRecord record = new ReagentBudgetRecord();
        record.setBudgetId(budgetId);
        record.setChangeType(changeType);
        record.setAmount(amount);
        record.setBeforeFrozen(beforeFrozen);
        record.setAfterFrozen(afterFrozen);
        record.setBeforeUsed(beforeUsed);
        record.setAfterUsed(afterUsed);
        record.setSourceType(sourceType);
        record.setSourceId(sourceId);
        record.setSourceNo(sourceNo);
        if (ctx != null) {
            record.setOperatorId(ctx.getOperatorId());
            record.setOperatorName(ctx.getOperatorName());
            record.setRemark(ctx.getRemark());
        }
        record.setCreateTime(LocalDateTime.now());
        budgetRecordMapper.insert(record);
    }

    private static BigDecimal nz(BigDecimal v) {
        return v == null ? BigDecimal.ZERO : v;
    }
}
