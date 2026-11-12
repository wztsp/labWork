package com.lis.reagent.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.lis.reagent.entity.ReagentBudgetRecord;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.math.BigDecimal;

/**
 * 预算变动流水 Mapper。
 *
 * <p>本表<b>只增不改不删</b>：Service 层不提供 update / delete 方法，
 * Controller 不暴露写端点（吸取存量缺陷 O 的教训）。</p>
 *
 * @author LIS Team
 */
@Mapper
public interface ReagentBudgetRecordMapper extends BaseMapper<ReagentBudgetRecord> {

    /**
     * 按变动类型汇总某预算的流水金额，用于 {@code reconcile()} 对账。
     *
     * @param budgetId   预算 ID
     * @param changeType 变动类型：1占用 2释放 3实扣 4调增 5调减
     * @return 汇总金额，无流水时返回 0（由 COALESCE 保证不为 null）
     */
    @Select("SELECT COALESCE(SUM(amount), 0) FROM lis_reagent_budget_record "
            + "WHERE budget_id = #{budgetId} AND change_type = #{changeType}")
    BigDecimal sumAmountByChangeType(@Param("budgetId") Long budgetId,
                                     @Param("changeType") Integer changeType);

    /**
     * 统计某预算的流水条数。
     *
     * @param budgetId 预算 ID
     * @return 流水条数
     */
    @Select("SELECT COUNT(1) FROM lis_reagent_budget_record WHERE budget_id = #{budgetId}")
    int countByBudget(@Param("budgetId") Long budgetId);

    /**
     * 由流水推算「已占用」金额 = SUM(占用) - SUM(释放) - SUM(实扣时释放的占用)。
     *
     * <p>说明：实扣（changeType=3）在业务上同时做了「释放占用」与「计入实扣」，
     * 因此推算 frozen 时需把实扣金额一并扣除。</p>
     *
     * @param budgetId 预算 ID
     * @return 推算的已占用金额，恒不为 null
     */
    @Select("SELECT COALESCE(SUM(CASE WHEN change_type = 1 THEN amount "
            + "                       WHEN change_type IN (2, 3) THEN -amount "
            + "                       ELSE 0 END), 0) "
            + "FROM lis_reagent_budget_record WHERE budget_id = #{budgetId}")
    BigDecimal calcFrozenFromRecords(@Param("budgetId") Long budgetId);

    /**
     * 由流水推算「已实扣」金额 = SUM(实扣)。
     *
     * @param budgetId 预算 ID
     * @return 推算的已实扣金额，恒不为 null
     */
    @Select("SELECT COALESCE(SUM(CASE WHEN change_type = 3 THEN amount ELSE 0 END), 0) "
            + "FROM lis_reagent_budget_record WHERE budget_id = #{budgetId}")
    BigDecimal calcUsedFromRecords(@Param("budgetId") Long budgetId);

    /**
     * 幂等断言：同一预算对同一来源单据的同一变动是否已存在流水。
     *
     * <p>配合唯一索引 {@code uk_lis_reagent_budget_record_src
     * (budget_id, change_type, source_type, source_id)}，实现「同一单据对同一预算的
     * 同一种变动只能发生一次」的数据库层幂等。Service 层先查后写（友好报错），
     * 极端并发下仍由唯一索引兜底（撞约束即整个事务回滚）。</p>
     *
     * @param budgetId   预算 ID
     * @param changeType 变动类型（{@link com.lis.reagent.enums.ChangeType}）
     * @param sourceType 来源类型（{@link com.lis.reagent.enums.SourceType}）
     * @param sourceId   来源单据 ID
     * @return 已存在的流水条数，0 表示尚未发生该变动
     */
    @Select("SELECT COUNT(1) FROM lis_reagent_budget_record "
            + "WHERE budget_id = #{budgetId} AND change_type = #{changeType} "
            + "  AND source_type = #{sourceType} AND source_id = #{sourceId}")
    int countBySource(@Param("budgetId") Long budgetId,
                      @Param("changeType") Integer changeType,
                      @Param("sourceType") String sourceType,
                      @Param("sourceId") Long sourceId);
}
