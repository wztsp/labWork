package com.lis.reagent.support;

import com.lis.common.core.exception.BusinessException;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * 预算判定纯函数工具类（零 Spring 依赖，可脱离容器直接单测）。
 *
 * <p>与 {@code QcQueryGuard} / {@code StatsQueryGuard} 同范式：{@code final} 类 +
 * 私有构造 + 全 {@code static} 方法，import 中不出现 {@code org.springframework.*}。</p>
 *
 * <p><b>使用约束</b>：本类只做「判定」，不做任何数据库读写。
 * R-03 预算管控的标准调用序列（四步，顺序不可调换）：</p>
 * <pre>
 * // ① 悲观行锁：在事务内锁住预算行，消除「读-判-写」之间的并发窗口
 * ReagentBudget budget = budgetMapper.selectForUpdate(year, deptId, categoryId);
 *
 * // ② T-08 红线：预算记录缺失 -> 抛「请先建立预算」，绝不按 0 额度静默拒绝
 * BudgetGuard.requireBudgetExists(budget, deptName, year);
 *
 * // ③ 纯函数判定：拿到 PASS / WARN / EXCEED 三态，用于给出精确的用户提示
 * BudgetGuard.Verdict v = BudgetGuard.judge(budget.getTotalAmount(),
 *         budget.getFrozenAmount(), budget.getUsedAmount(), amount, budget.getWarnRatio());
 * if (!v.isPassable()) {
 *     throw new BusinessException("预算不足，超出 "
 *             + BudgetGuard.excessAmount(budget.getTotalAmount(), budget.getFrozenAmount(),
 *                                        budget.getUsedAmount(), amount) + " 元");
 * }
 *
 * // ④ CAS 兜底：UPDATE ... WHERE frozen + used + amount &lt;= total，
 * //    受影响行数必须为 1；为 0 说明判定与 SQL 条件不一致（代码 bug），立即抛异常回滚
 * if (budgetMapper.tryFreeze(budget.getId(), amount) != 1) {
 *     throw new BusinessException("预算占用失败，请重试");
 * }
 * </pre>
 * <p>①④ 为 D+E 双保险（详见 {@code ReagentBudgetMapper} 类注释与设计文档 §2.2.3）：
 * 即便 ③ 的判定逻辑有 bug，④ 的 SQL 条件也不会让预算超支。</p>
 *
 * <p><b>金额比较规则</b>：一律使用 {@link BigDecimal#compareTo}，
 * 禁止 {@code double} 运算与 {@link BigDecimal#equals}（后者会因标度不同而误判，
 * 例如 {@code new BigDecimal("1.0").equals(new BigDecimal("1.00")) == false}）。</p>
 *
 * @author LIS Team
 */
public final class BudgetGuard {

    /** 比例计算保留小数位 */
    private static final int RATIO_SCALE = 4;

    /** 默认预警阈值，当预算未配置 warnRatio 时使用（80%） */
    public static final BigDecimal DEFAULT_WARN_RATIO = new BigDecimal("0.80");

    private BudgetGuard() {
        throw new AssertionError("工具类禁止实例化");
    }

    /**
     * 预算判定结果三态。
     */
    public enum Verdict {

        /** 通过：占用后仍低于预警线 */
        PASS("PASS", "通过"),
        /** 预警：占用后达到或超过预警线，但未超总额，放行并提示 */
        WARN("WARN", "预警"),
        /** 超额：占用后突破总额，必须拦截 */
        EXCEED("EXCEED", "超预算");

        private final String code;
        private final String label;

        Verdict(String code, String label) {
            this.code = code;
            this.label = label;
        }

        public String getCode() {
            return code;
        }

        public String getLabel() {
            return label;
        }

        /** 是否放行（PASS 与 WARN 均放行，仅 EXCEED 拦截） */
        public boolean isPassable() {
            return this != EXCEED;
        }
    }

    /**
     * 【T-08 红线决策】预算记录存在性校验 —— <b>必须在 {@link #judge} 之前调用</b>。
     *
     * <p><b>决策：预算记录缺失时抛业务异常，绝不按「零额度」静默处理。</b></p>
     *
     * <p><b>为什么这是红线</b>：<br>
     * {@link #judge} 对 null 金额一律按 0 处理，因此「预算记录不存在」与
     * 「预算总额为 0」在它眼里完全等价，两者都会返回 {@link Verdict#EXCEED}。
     * 若调用方图省事直接把 null 预算对象的字段透传进去，新科室第一次采购时
     * 页面会弹出<b>「预算不足」</b>——这是一句<b>错误且极具误导性</b>的提示：
     * 科室主任会去找财务追加预算，财务查账却发现该科室<b>压根没建过预算记录</b>，
     * 双方来回扯皮，真实原因（主数据缺失）被完全掩盖。
     * 正确的提示必须直指根因：「请先为该科室建立预算」。</p>
     *
     * <p><b>为什么不能反过来「无预算即放行」</b>：那等于预算管控对所有未建预算的科室失效，
     * R-03 的管控目标被架空，比误报更严重。</p>
     *
     * <p>综上，缺失是<b>配置错误</b>而非<b>额度耗尽</b>，只能由人工补建预算解决，
     * 系统必须快速失败并给出可操作的指引，不得猜测意图。</p>
     *
     * @param budget   预算对象，允许为 null（正是本方法要拦截的情形）
     * @param deptName 科室名称，用于拼装可操作的错误提示；为空时降级显示「未知科室」
     * @param year     预算年度，用于错误提示定位
     * @throws BusinessException 预算记录不存在时抛出，提示明确指向「请先建立预算」
     */
    public static void requireBudgetExists(Object budget, String deptName, Integer year) {
        if (budget == null) {
            String dept = (deptName == null || deptName.trim().isEmpty()) ? "未知科室" : deptName.trim();
            String yearPart = (year == null) ? "" : (year + " 年度");
            throw new BusinessException(
                    "预算记录不存在，请先为科室[" + dept + "]建立" + yearPart + "预算");
        }
    }

    /**
     * 纯函数判定：本次拟占用 {@code amount} 后预算是否安全。
     *
     * <p><b>预算记录缺失（整条 budget 为 null）的情形本方法不处理</b>：本方法只接收已经取到的
     * 金额字段（total / frozen / used）。当预算记录本身不存在时，调用方
     * <b>必须</b>先调用 {@link #requireBudgetExists} 抛出明确异常，
     * <b>绝不允许</b>把 null 预算对象的字段透传进来——字段为 null 时本方法按 0 处理，
     * 会把「预算未建立」误报成「预算不足」（T-08 红线，理由详见
     * {@link #requireBudgetExists} 的方法注释）。</p>
     *
     * <p>判定顺序（顺序不可调换）：</p>
     * <ol>
     *   <li>预计占用 = frozen + used + amount；若 &gt; total 则 EXCEED</li>
     *   <li>total 为 0（或未配置）时，任何正数占用都视为 EXCEED —— 未分配预算等于零额度</li>
     *   <li>占用率 = 预计占用 / total；&ge; warnRatio 则 WARN，否则 PASS</li>
     * </ol>
     *
     * @param total     预算总额，null 视为 0
     * @param frozen    已占用（冻结）金额，null 视为 0
     * @param used      已实扣金额，null 视为 0
     * @param amount    本次拟占用金额，null 视为 0
     * @param warnRatio 预警阈值（如 0.80 表示 80%），null 时取 {@link #DEFAULT_WARN_RATIO}
     * @return 判定结果，不会返回 null
     */
    public static Verdict judge(BigDecimal total, BigDecimal frozen, BigDecimal used,
                                BigDecimal amount, BigDecimal warnRatio) {
        BigDecimal totalVal = nz(total);
        BigDecimal projected = nz(frozen).add(nz(used)).add(nz(amount));

        if (projected.compareTo(totalVal) > 0) {
            return Verdict.EXCEED;
        }
        // 总额为 0：只有本次占用也为 0 才算通过，否则等同于超额
        if (totalVal.compareTo(BigDecimal.ZERO) == 0) {
            return projected.compareTo(BigDecimal.ZERO) == 0 ? Verdict.PASS : Verdict.EXCEED;
        }

        BigDecimal effectiveWarn = warnRatio == null ? DEFAULT_WARN_RATIO : warnRatio;
        BigDecimal ratio = projected.divide(totalVal, RATIO_SCALE, RoundingMode.HALF_UP);
        return ratio.compareTo(effectiveWarn) >= 0 ? Verdict.WARN : Verdict.PASS;
    }

    /**
     * 超额金额 = 预计占用 - 预算总额。供页面明示「超出多少」（R-03 验收 7）。
     *
     * @param total  预算总额，null 视为 0
     * @param frozen 已占用金额，null 视为 0
     * @param used   已实扣金额，null 视为 0
     * @param amount 本次拟占用金额，null 视为 0
     * @return 超额金额；未超额时返回 {@link BigDecimal#ZERO}，恒不为负、不为 null
     */
    public static BigDecimal excessAmount(BigDecimal total, BigDecimal frozen,
                                          BigDecimal used, BigDecimal amount) {
        BigDecimal projected = nz(frozen).add(nz(used)).add(nz(amount));
        BigDecimal excess = projected.subtract(nz(total));
        return excess.compareTo(BigDecimal.ZERO) > 0 ? excess : BigDecimal.ZERO;
    }

    /**
     * 当前占用率 = (frozen + used) / total，不含本次拟占用金额。
     *
     * <p>用于预算看板展示。total 为 0 时返回 {@link BigDecimal#ZERO}，避免除零异常。</p>
     *
     * @param total  预算总额，null 视为 0
     * @param frozen 已占用金额，null 视为 0
     * @param used   已实扣金额，null 视为 0
     * @return 占用率，标度 4 位，恒不为 null
     */
    public static BigDecimal usageRatio(BigDecimal total, BigDecimal frozen, BigDecimal used) {
        BigDecimal totalVal = nz(total);
        if (totalVal.compareTo(BigDecimal.ZERO) == 0) {
            return BigDecimal.ZERO;
        }
        return nz(frozen).add(nz(used)).divide(totalVal, RATIO_SCALE, RoundingMode.HALF_UP);
    }

    /**
     * 剩余可用额度 = total - frozen - used。
     *
     * @param total  预算总额，null 视为 0
     * @param frozen 已占用金额，null 视为 0
     * @param used   已实扣金额，null 视为 0
     * @return 剩余额度，可能为负（表示历史数据已超支），恒不为 null
     */
    public static BigDecimal available(BigDecimal total, BigDecimal frozen, BigDecimal used) {
        return nz(total).subtract(nz(frozen)).subtract(nz(used));
    }

    /**
     * 校验金额必须为正数，用于占用 / 释放 / 实扣的入参前置校验。
     *
     * @param amount 待校验金额
     * @param field  字段中文名，用于异常提示
     * @throws BusinessException 金额为 null 或非正数时抛出
     */
    public static void requirePositive(BigDecimal amount, String field) {
        if (amount == null) {
            throw new BusinessException(field + "不能为空");
        }
        if (amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new BusinessException(field + "必须大于 0");
        }
    }

    /**
     * null 安全归零。
     *
     * @param v 原值
     * @return v 为 null 时返回 {@link BigDecimal#ZERO}，否则原样返回
     */
    private static BigDecimal nz(BigDecimal v) {
        return v == null ? BigDecimal.ZERO : v;
    }
}
