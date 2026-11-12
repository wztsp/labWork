package com.lis.reagent.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.lis.reagent.entity.ReagentBudget;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.math.BigDecimal;

/**
 * 试剂预算 Mapper —— 本模块并发安全的核心。
 *
 * <p><b>并发方案（设计文档 §2.2.3，D + E 双保险）</b>：</p>
 * <ol>
 *   <li><b>D 主方案</b>：{@link #selectByIdForUpdate} / {@link #selectForUpdate} 用
 *       {@code SELECT ... FOR UPDATE} 悲观行锁在事务内锁住预算行，
 *       让「读 - 判 - 写」之间零窗口，同时能拿到精确的 before 快照写流水。</li>
 *   <li><b>E 兜底断言</b>：{@link #tryFreeze} 等 UPDATE 均附加金额条件，
 *       受影响行数必须为 1；为 0 说明判定逻辑与 SQL 条件不一致（代码 bug）
 *       或行锁失效，调用方必须立即抛异常回滚，绝不静默放过。</li>
 * </ol>
 *
 * <p><b>约束</b>：全部使用 {@code #{}} 参数绑定，无任何字符串拼接；
 * {@code FOR UPDATE} 只允许出现在预算占用 / 释放 / 实扣的同一事务内。</p>
 *
 * @author LIS Team
 */
@Mapper
public interface ReagentBudgetMapper extends BaseMapper<ReagentBudget> {

    /**
     * 悲观锁加载预算行。<b>必须在事务内调用</b>，否则行锁会立即释放，失去保护意义。
     *
     * <p>{@code FOR UPDATE} 会阻塞其他事务对同一行的 {@code FOR UPDATE} / {@code UPDATE}，
     * 直到本事务提交或回滚。</p>
     *
     * @param id 预算 ID
     * @return 预算行；不存在返回 null
     */
    @Select("SELECT * FROM lis_reagent_budget WHERE id = #{id} FOR UPDATE")
    ReagentBudget selectByIdForUpdate(@Param("id") Long id);

    /**
     * 按「年度 + 科室 + 类别」三元组定位并加锁，用于提交申请时匹配预算。
     *
     * <p>{@code categoryId} 为 NULL 表示匹配「不限类别」的通用预算。
     * 这里用 {@code (category_id = #{categoryId} OR (#{categoryId} IS NULL AND category_id IS NULL))}
     * 而不是简单的 {@code category_id = #{categoryId}}，是因为 SQL 中
     * {@code NULL = NULL} 结果为 UNKNOWN 而非 TRUE，直接等值比较会永远匹配不到通用预算。</p>
     *
     * @param year       预算年度
     * @param deptId     科室 ID
     * @param categoryId 类别 ID，可为 null
     * @return 预算行；不存在返回 null
     */
    @Select("SELECT * FROM lis_reagent_budget "
            + "WHERE budget_year = #{year} AND dept_id = #{deptId} AND status = 1 "
            + "  AND (category_id = #{categoryId} OR (#{categoryId} IS NULL AND category_id IS NULL)) "
            + "FOR UPDATE")
    ReagentBudget selectForUpdate(@Param("year") Integer year,
                                  @Param("deptId") Long deptId,
                                  @Param("categoryId") Long categoryId);

    /**
     * 原子占用（冻结）。超预算时受影响行数为 0。
     *
     * <p>这是方案 E 的兜底断言 —— 即使 {@code BudgetGuard} 判定逻辑有 bug，
     * 这条 SQL 的 {@code frozen + used + amount <= total} 条件也不会让预算超支。</p>
     *
     * @param id     预算 ID
     * @param amount 占用金额，必须为正数
     * @return 受影响行数，正常为 1；为 0 表示超额或预算已停用
     */
    @Update("UPDATE lis_reagent_budget SET frozen_amount = frozen_amount + #{amount}, "
            + "       version = version + 1, update_time = CURRENT_TIMESTAMP "
            + " WHERE id = #{id} AND status = 1 "
            + "   AND frozen_amount + used_amount + #{amount} <= total_amount")
    int tryFreeze(@Param("id") Long id, @Param("amount") BigDecimal amount);

    /**
     * 原子释放占用（驳回 / 作废时调用），带下限保护防止 frozen 变负数。
     *
     * @param id     预算 ID
     * @param amount 释放金额，必须为正数
     * @return 受影响行数，正常为 1；为 0 表示 frozen 不足（数据异常）
     */
    @Update("UPDATE lis_reagent_budget SET frozen_amount = frozen_amount - #{amount}, "
            + "       version = version + 1, update_time = CURRENT_TIMESTAMP "
            + " WHERE id = #{id} AND frozen_amount >= #{amount}")
    int tryRelease(@Param("id") Long id, @Param("amount") BigDecimal amount);

    /**
     * 原子实扣：占用转实扣，两个字段同时变动，带下限保护。
     *
     * <p>入库验收通过时调用。{@code freezeAmount} 与 {@code usedAmount} 允许不等
     * （实际到货金额可能与申请预估金额有差异）。</p>
     *
     * @param id           预算 ID
     * @param freezeAmount 要释放的占用金额
     * @param usedAmount   要计入的实扣金额
     * @return 受影响行数，正常为 1；为 0 表示 frozen 不足
     */
    @Update("UPDATE lis_reagent_budget SET frozen_amount = frozen_amount - #{freezeAmount}, "
            + "       used_amount = used_amount + #{usedAmount}, "
            + "       version = version + 1, update_time = CURRENT_TIMESTAMP "
            + " WHERE id = #{id} AND frozen_amount >= #{freezeAmount}")
    int trySettle(@Param("id") Long id,
                  @Param("freezeAmount") BigDecimal freezeAmount,
                  @Param("usedAmount") BigDecimal usedAmount);

    /**
     * 原子调增预算总额（财务追加预算，供已冻结申请解冻后重新判定）。
     *
     * @param id     预算 ID
     * @param amount 调增金额，必须为正数
     * @return 受影响行数，正常为 1
     */
    @Update("UPDATE lis_reagent_budget SET total_amount = total_amount + #{amount}, "
            + "       version = version + 1, update_time = CURRENT_TIMESTAMP "
            + " WHERE id = #{id}")
    int tryIncrease(@Param("id") Long id, @Param("amount") BigDecimal amount);

    /**
     * 原子调减预算总额，带下限保护：调减后不得低于已占用 + 已实扣。
     *
     * @param id     预算 ID
     * @param amount 调减金额，必须为正数
     * @return 受影响行数，正常为 1；为 0 表示调减后会低于已用额度
     */
    @Update("UPDATE lis_reagent_budget SET total_amount = total_amount - #{amount}, "
            + "       version = version + 1, update_time = CURRENT_TIMESTAMP "
            + " WHERE id = #{id} AND total_amount - #{amount} >= frozen_amount + used_amount")
    int tryDecrease(@Param("id") Long id, @Param("amount") BigDecimal amount);
}
