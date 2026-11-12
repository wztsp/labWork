package com.lis.reagent.support;

import com.lis.common.core.exception.BusinessException;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/**
 * 审批节点解析器（零 Spring 依赖，可脱离容器直接单测）。
 *
 * <p>把 {@code lis_reagent_approval_node} 配置表里的全量节点，按当前单据上下文
 * （是否超预算、是否含危险品）过滤成一条实际生效的线性审批链，并重新编号为连续 1..n。</p>
 *
 * <p><b>关键设计</b>：{@code triggerCond} 使用固定枚举（ALWAYS / OVER_BUDGET / HAZARD），
 * <b>绝不引入表达式引擎</b>（SpEL / Aviator / Drools）。条件分支只有 3 种，
 * 枚举足够表达，且可静态穷举单测。</p>
 *
 * @author LIS Team
 */
public final class ApprovalNodeResolver {

    private ApprovalNodeResolver() {
        throw new AssertionError("工具类禁止实例化");
    }

    /**
     * 节点触发条件枚举。禁止扩展为表达式字符串。
     */
    public enum TriggerCond {

        /** 恒定触发 */
        ALWAYS("ALWAYS", "总是"),
        /** 仅当单据超预算时触发 */
        OVER_BUDGET("OVER_BUDGET", "超预算时"),
        /** 仅当单据含危险品时触发 */
        HAZARD("HAZARD", "含危险品时");

        private final String code;
        private final String label;

        TriggerCond(String code, String label) {
            this.code = code;
            this.label = label;
        }

        public String getCode() {
            return code;
        }

        public String getLabel() {
            return label;
        }

        /**
         * 按条件码解析。
         *
         * @param code 条件码，null / 空串 / 未知值一律按 {@link #ALWAYS} 处理，
         *             保证配置表脏数据不会让审批链凭空消失（fail-safe 而非 fail-open）
         * @return 匹配的枚举，恒不为 null
         */
        public static TriggerCond of(String code) {
            if (code == null || code.trim().isEmpty()) {
                return ALWAYS;
            }
            String key = code.trim();
            for (TriggerCond c : values()) {
                if (c.code.equalsIgnoreCase(key)) {
                    return c;
                }
            }
            return ALWAYS;
        }
    }

    /**
     * 审批节点规格：配置表行的纯 POJO 投影，不含任何持久化注解。
     */
    public static final class ApprovalNodeSpec {

        /** 配置表主键，可为 null（单测构造时） */
        private Long nodeId;
        /** 节点顺序，解析后会被重新编号为连续 1..n */
        private int nodeLevel;
        /** 节点名称，如「科室主任」「分管院长」「安全员」 */
        private String nodeName;
        /** 触发条件，恒不为 null */
        private TriggerCond triggerCond = TriggerCond.ALWAYS;
        /** 审批人角色编码，对应 sys_role.role_code（主理人 U-4：复用现有角色体系） */
        private String approverRoleCode;
        /** 审批人科室范围：SELF 本科室 / ALL 全院 / 指定科室编码 */
        private String approverDeptScope;

        public ApprovalNodeSpec() {
        }

        /**
         * 全参构造，便于单测直接 new。
         *
         * @param nodeId            配置表主键
         * @param nodeLevel         节点顺序
         * @param nodeName          节点名称
         * @param triggerCond       触发条件，null 视为 ALWAYS
         * @param approverRoleCode  审批人角色编码
         * @param approverDeptScope 审批人科室范围
         */
        public ApprovalNodeSpec(Long nodeId, int nodeLevel, String nodeName, TriggerCond triggerCond,
                                String approverRoleCode, String approverDeptScope) {
            this.nodeId = nodeId;
            this.nodeLevel = nodeLevel;
            this.nodeName = nodeName;
            this.triggerCond = triggerCond == null ? TriggerCond.ALWAYS : triggerCond;
            this.approverRoleCode = approverRoleCode;
            this.approverDeptScope = approverDeptScope;
        }

        public Long getNodeId() {
            return nodeId;
        }

        public void setNodeId(Long nodeId) {
            this.nodeId = nodeId;
        }

        public int getNodeLevel() {
            return nodeLevel;
        }

        public void setNodeLevel(int nodeLevel) {
            this.nodeLevel = nodeLevel;
        }

        public String getNodeName() {
            return nodeName;
        }

        public void setNodeName(String nodeName) {
            this.nodeName = nodeName;
        }

        public TriggerCond getTriggerCond() {
            return triggerCond;
        }

        public void setTriggerCond(TriggerCond triggerCond) {
            this.triggerCond = triggerCond == null ? TriggerCond.ALWAYS : triggerCond;
        }

        public String getApproverRoleCode() {
            return approverRoleCode;
        }

        public void setApproverRoleCode(String approverRoleCode) {
            this.approverRoleCode = approverRoleCode;
        }

        public String getApproverDeptScope() {
            return approverDeptScope;
        }

        public void setApproverDeptScope(String approverDeptScope) {
            this.approverDeptScope = approverDeptScope;
        }
    }

    /**
     * 审批上下文：决定哪些条件节点被激活。
     */
    public static final class ApprovalContext {

        /** 单据是否超预算 */
        private final boolean overBudget;
        /** 单据是否含危险品 */
        private final boolean hazard;

        /**
         * @param overBudget 是否超预算
         * @param hazard     是否含危险品
         */
        public ApprovalContext(boolean overBudget, boolean hazard) {
            this.overBudget = overBudget;
            this.hazard = hazard;
        }

        public boolean isOverBudget() {
            return overBudget;
        }

        public boolean isHazard() {
            return hazard;
        }

        /** 普通上下文：既不超预算也不含危险品 */
        public static ApprovalContext plain() {
            return new ApprovalContext(false, false);
        }
    }

    /**
     * 解析生效审批链。
     *
     * <p>处理步骤：①按 {@code triggerCond} 与上下文过滤 → ②按原 {@code nodeLevel} 升序排序
     * → ③重新编号为连续 1..n（避免配置表里 level 有断号导致 currentNodeLevel 永远追不上）。</p>
     *
     * <p>返回的是<b>全新对象</b>，不修改入参元素，避免污染调用方缓存的配置列表。</p>
     *
     * @param allNodes 某 bizType 下的全部启用节点，可为 null 或空
     * @param ctx      审批上下文，不能为 null
     * @return 重新编号后的生效节点列表，可能为空列表（表示无需审批），恒不为 null
     * @throws BusinessException ctx 为 null 时抛出
     */
    public static List<ApprovalNodeSpec> resolve(List<ApprovalNodeSpec> allNodes, ApprovalContext ctx) {
        if (ctx == null) {
            throw new BusinessException("审批上下文不能为空");
        }
        if (allNodes == null || allNodes.isEmpty()) {
            return Collections.emptyList();
        }

        List<ApprovalNodeSpec> matched = new ArrayList<>(allNodes.size());
        for (ApprovalNodeSpec node : allNodes) {
            if (node == null) {
                continue;
            }
            if (isTriggered(node.getTriggerCond(), ctx)) {
                matched.add(node);
            }
        }
        if (matched.isEmpty()) {
            return Collections.emptyList();
        }

        matched.sort(Comparator.comparingInt(ApprovalNodeSpec::getNodeLevel));

        List<ApprovalNodeSpec> result = new ArrayList<>(matched.size());
        int level = 1;
        for (ApprovalNodeSpec node : matched) {
            result.add(new ApprovalNodeSpec(node.getNodeId(), level, node.getNodeName(),
                    node.getTriggerCond(), node.getApproverRoleCode(), node.getApproverDeptScope()));
            level++;
        }
        return result;
    }

    /**
     * 判断某节点在当前上下文下是否被触发。
     *
     * @param cond 触发条件，null 视为 ALWAYS
     * @param ctx  审批上下文，不能为 null
     * @return true 表示该节点生效
     */
    public static boolean isTriggered(TriggerCond cond, ApprovalContext ctx) {
        if (ctx == null) {
            throw new BusinessException("审批上下文不能为空");
        }
        TriggerCond effective = cond == null ? TriggerCond.ALWAYS : cond;
        switch (effective) {
            case OVER_BUDGET:
                return ctx.isOverBudget();
            case HAZARD:
                return ctx.isHazard();
            case ALWAYS:
            default:
                return true;
        }
    }

    /**
     * 判断当前节点是否为审批链末节点。
     *
     * @param currentNodeLevel 当前节点序号（1 起）
     * @param totalNodeCount   审批链总节点数
     * @return true 表示已到末节点，审批通过后单据进入「已批准」
     */
    public static boolean isLastNode(int currentNodeLevel, int totalNodeCount) {
        return totalNodeCount <= 0 || currentNodeLevel >= totalNodeCount;
    }

    /**
     * 取审批链中指定序号的节点。
     *
     * @param nodes     生效节点列表，可为 null
     * @param nodeLevel 节点序号（1 起）
     * @return 匹配的节点；无匹配返回 null
     */
    public static ApprovalNodeSpec nodeAt(List<ApprovalNodeSpec> nodes, int nodeLevel) {
        if (nodes == null || nodes.isEmpty() || nodeLevel < 1 || nodeLevel > nodes.size()) {
            return null;
        }
        return nodes.get(nodeLevel - 1);
    }
}
