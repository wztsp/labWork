package com.lis.reagent.support;

import com.lis.common.core.exception.BusinessException;
import com.lis.reagent.enums.ApplyStatus;
import com.lis.reagent.enums.ApprovalAction;

import java.util.Collections;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

/**
 * 采购申请单轻量状态机（零 Spring 依赖，可脱离容器直接单测）。
 *
 * <p>项目只有 2 条固定审批链、节点数 2~3 级、无会签、无并行网关、无动态表单，
 * 因此不引入 Flowable / Activiti 等工作流引擎，用配置表 + 线性状态机即可（设计文档 §2.1）。</p>
 *
 * <pre>
 * 0 草稿 ──submit──▶ [预算判定] ──超预算──▶ 5 已冻结 ──unfreeze──▶ 回到 [预算判定]
 *                        └──通过/预警──▶ 1 待审批
 *                                          ├──approve(非末节点)──▶ 2 审批中
 *                                          ├──approve(末节点)────▶ 3 已批准
 *                                          └──reject────────────▶ 4 已驳回 ──resubmit──▶ [预算判定]
 * 任意非终态 ──cancel──▶ 6 已作废（释放占用）
 * </pre>
 *
 * @author LIS Team
 */
public final class PurchaseApplyStateMachine {

    /** 允许编辑明细的状态集合：仅草稿与已驳回 */
    private static final Set<ApplyStatus> EDITABLE =
            Collections.unmodifiableSet(EnumSet.of(ApplyStatus.DRAFT, ApplyStatus.REJECTED));

    /** 各状态允许的动作表 */
    private static final Map<ApplyStatus, Set<ApprovalAction>> ALLOWED;

    static {
        Map<ApplyStatus, Set<ApprovalAction>> m = new EnumMap<>(ApplyStatus.class);
        m.put(ApplyStatus.DRAFT, EnumSet.of(ApprovalAction.SUBMIT, ApprovalAction.CANCEL));
        m.put(ApplyStatus.PENDING, EnumSet.of(ApprovalAction.APPROVE, ApprovalAction.REJECT, ApprovalAction.CANCEL));
        m.put(ApplyStatus.APPROVING, EnumSet.of(ApprovalAction.APPROVE, ApprovalAction.REJECT, ApprovalAction.CANCEL));
        m.put(ApplyStatus.REJECTED, EnumSet.of(ApprovalAction.RESUBMIT, ApprovalAction.CANCEL));
        m.put(ApplyStatus.FROZEN, EnumSet.of(ApprovalAction.UNFREEZE, ApprovalAction.CANCEL));
        // 终态无任何可执行动作
        m.put(ApplyStatus.APPROVED, EnumSet.noneOf(ApprovalAction.class));
        m.put(ApplyStatus.CANCELLED, EnumSet.noneOf(ApprovalAction.class));

        Map<ApplyStatus, Set<ApprovalAction>> immutable = new EnumMap<>(ApplyStatus.class);
        for (Map.Entry<ApplyStatus, Set<ApprovalAction>> e : m.entrySet()) {
            immutable.put(e.getKey(), Collections.unmodifiableSet(e.getValue()));
        }
        ALLOWED = Collections.unmodifiableMap(immutable);
    }

    private PurchaseApplyStateMachine() {
        throw new AssertionError("工具类禁止实例化");
    }

    /**
     * 计算流转后的目标状态。
     *
     * <p>注意：{@link ApprovalAction#SUBMIT} 与 {@link ApprovalAction#RESUBMIT} 返回
     * {@link ApplyStatus#PENDING}，代表「预算判定通过」这一分支。若预算判定为超额，
     * 调用方（Service）应改置 {@link ApplyStatus#FROZEN}，而不是由本方法决定 ——
     * 状态机不感知金额，保持纯粹。</p>
     *
     * @param current    当前状态，不能为 null
     * @param action     执行动作，不能为 null
     * @param isLastNode 当前是否为审批链末节点，仅 {@link ApprovalAction#APPROVE} 时有意义
     * @return 目标状态，不会返回 null
     * @throws BusinessException 入参为 null 或流转非法时抛出
     */
    public static ApplyStatus next(ApplyStatus current, ApprovalAction action, boolean isLastNode) {
        assertTransition(current, action);

        switch (action) {
            case SUBMIT:
            case RESUBMIT:
            case UNFREEZE:
                // 三者都回到「预算判定」入口，判定通过即待审批；超额由 Service 改置 FROZEN
                return ApplyStatus.PENDING;
            case APPROVE:
                return isLastNode ? ApplyStatus.APPROVED : ApplyStatus.APPROVING;
            case REJECT:
                return ApplyStatus.REJECTED;
            case CANCEL:
                return ApplyStatus.CANCELLED;
            default:
                throw new BusinessException("未支持的审批动作：" + action);
        }
    }

    /**
     * 查询某状态下允许执行的动作集合，供前端按钮置灰使用。
     *
     * @param current 当前状态，可为 null
     * @return 不可变的动作集合；current 为 null 或未知时返回空集，不会返回 null
     */
    public static Set<ApprovalAction> allowedActions(ApplyStatus current) {
        if (current == null) {
            return Collections.emptySet();
        }
        Set<ApprovalAction> actions = ALLOWED.get(current);
        return actions == null ? Collections.emptySet() : actions;
    }

    /**
     * 断言流转合法，非法立即抛出业务异常。
     *
     * @param from   源状态，不能为 null
     * @param action 执行动作，不能为 null
     * @throws BusinessException 入参为 null 或该状态不允许此动作时抛出
     */
    public static void assertTransition(ApplyStatus from, ApprovalAction action) {
        if (from == null) {
            throw new BusinessException("单据状态不能为空");
        }
        if (action == null) {
            throw new BusinessException("审批动作不能为空");
        }
        if (!allowedActions(from).contains(action)) {
            throw new BusinessException("单据当前状态[" + from.getLabel()
                    + "]不允许执行[" + action.getLabel() + "]操作");
        }
    }

    /**
     * 断言当前状态允许编辑明细（R-02 验收 2：提交后申请人无法再修改明细）。
     *
     * @param status 当前状态，不能为 null
     * @throws BusinessException 状态为 null 或不可编辑时抛出
     */
    public static void assertEditable(ApplyStatus status) {
        if (status == null) {
            throw new BusinessException("单据状态不能为空");
        }
        if (!EDITABLE.contains(status)) {
            throw new BusinessException("单据当前状态[" + status.getLabel() + "]不允许修改明细");
        }
    }

    /**
     * 当前状态是否允许编辑明细（不抛异常版本，供前端按钮控制）。
     *
     * @param status 当前状态，可为 null
     * @return true 表示可编辑
     */
    public static boolean isEditable(ApplyStatus status) {
        return status != null && EDITABLE.contains(status);
    }
}
