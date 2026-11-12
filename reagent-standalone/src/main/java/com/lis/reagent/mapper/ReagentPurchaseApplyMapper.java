package com.lis.reagent.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.lis.reagent.entity.ReagentPurchaseApply;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.math.BigDecimal;

/**
 * 采购申请单 Mapper。
 *
 * <p>动态列表查询一律由 ServiceImpl 用 {@code LambdaQueryWrapper} 构造，本接口只承载
 * MP 无法表达的聚合 / 悲观锁类自定义 SQL；所有参数使用 {@code #{}} 预编译绑定，
 * 严禁字符串拼接（T-05 验收标准 ②）。</p>
 *
 * @author LIS Team
 */
@Mapper
public interface ReagentPurchaseApplyMapper extends BaseMapper<ReagentPurchaseApply> {

    /**
     * 统计某预算下「已占用（冻结中或已执行）」的申请单金额合计，用于预算对账（BudgetReconcileVO）。
     *
     * <p>口径（状态码见 {@link com.lis.reagent.enums.ApplyStatus}）：仅统计
     * {@link com.lis.reagent.enums.ApplyStatus#PENDING}(1) /
     * {@link com.lis.reagent.enums.ApplyStatus#APPROVING}(2) /
     * {@link com.lis.reagent.enums.ApplyStatus#APPROVED}(3) 三类真正占用预算的状态；
     * {@link com.lis.reagent.enums.ApplyStatus#DRAFT}(0)、
     * {@link com.lis.reagent.enums.ApplyStatus#REJECTED}(4)、
     * {@link com.lis.reagent.enums.ApplyStatus#CANCELLED}(6) 从未占用，
     * {@link com.lis.reagent.enums.ApplyStatus#FROZEN}(5) 为超预算被拦下、
     * 同样未产生 freeze 流水，故一并排除；禁止散落魔法数字（共享知识第 14 条）。</p>
     *
     * @param budgetId 预算 ID，不可为 null
     * @return 金额合计，无数据时返回 0
     */
    @Select("SELECT COALESCE(SUM(total_amount), 0) FROM lis_reagent_purchase_apply "
            + "WHERE budget_id = #{budgetId} AND status IN (1, 2, 3)")
    BigDecimal sumOccupiedAmountByBudget(@Param("budgetId") Long budgetId);

    /**
     * 统计某预算下处于指定状态的申请单数量。
     *
     * @param budgetId 预算 ID，不可为 null
     * @param status   申请单状态码，不可为 null
     * @return 数量，无数据时返回 0
     */
    @Select("SELECT COUNT(1) FROM lis_reagent_purchase_apply "
            + "WHERE budget_id = #{budgetId} AND status = #{status}")
    int countByBudgetAndStatus(@Param("budgetId") Long budgetId, @Param("status") Integer status);

    /**
     * 悲观行锁读取申请单，用于审批 / 撤回等并发写场景，必须在同一事务内调用。
     *
     * @param id 申请单 ID，不可为 null
     * @return 加锁后的申请单，不存在时返回 null
     */
    @Select("SELECT * FROM lis_reagent_purchase_apply WHERE id = #{id} FOR UPDATE")
    ReagentPurchaseApply selectByIdForUpdate(@Param("id") Long id);

    /**
     * 取当日同前缀的最大单号，供 {@code com.lis.reagent.support.BizNoGenerator} 续号。
     *
     * @param head 前缀 + yyyyMMdd 头部（如 {@code PA20260804}），不可为 null
     * @return 最大单号；当日尚无单据时返回 null
     */
    @Select("SELECT MAX(apply_no) FROM lis_reagent_purchase_apply WHERE apply_no LIKE #{head} || '%'")
    String selectMaxNo(@Param("head") String head);
}
