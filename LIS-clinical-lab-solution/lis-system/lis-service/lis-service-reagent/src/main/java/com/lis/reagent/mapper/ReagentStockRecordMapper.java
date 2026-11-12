package com.lis.reagent.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.lis.reagent.entity.ReagentStockRecord;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Mapper
public interface ReagentStockRecordMapper extends BaseMapper<ReagentStockRecord> {

    /**
     * 汇总某业务单据已产生的出入库流水数量合计，用于「单据 ↔ 流水」一致性对账。
     *
     * @param relatedType 关联单据类型（如 {@code REQUISITION} / {@code INBOUND}），不可为 null
     * @param relatedId   关联单据 ID，不可为 null
     * @return 数量合计，无流水时返回 0
     */
    @Select("SELECT COALESCE(SUM(quantity), 0) FROM lis_reagent_stock_record "
            + "WHERE related_type = #{relatedType} AND related_id = #{relatedId}")
    BigDecimal sumQtyByRelated(@Param("relatedType") String relatedType,
                               @Param("relatedId") Long relatedId);

    /**
     * 统计某业务单据已产生的流水条数，用于执行动作的幂等防重。
     *
     * @param relatedType 关联单据类型，不可为 null
     * @param relatedId   关联单据 ID，不可为 null
     * @return 流水条数，无流水时返回 0
     */
    @Select("SELECT COUNT(1) FROM lis_reagent_stock_record "
            + "WHERE related_type = #{relatedType} AND related_id = #{relatedId}")
    int countByRelated(@Param("relatedType") String relatedType,
                       @Param("relatedId") Long relatedId);

    /**
     * 按科室 + 流水类型汇总数量，供科室级消耗统计报表使用。
     *
     * @param deptId     科室 ID，不可为 null
     * @param recordType 流水类型码，不可为 null
     * @return 数量合计，无数据时返回 0
     */
    @Select("SELECT COALESCE(SUM(quantity), 0) FROM lis_reagent_stock_record "
            + "WHERE dept_id = #{deptId} AND record_type = #{recordType}")
    BigDecimal sumQtyByDeptAndType(@Param("deptId") Long deptId,
                                   @Param("recordType") Integer recordType);

    /**
     * 领用趋势（T-18 / R-11）：按日聚合 {@code record_type = 2}（领用出库）数量。
     *
     * <p>出库数量可能为负（库存扣减），一律取绝对值 {@code ABS(quantity)} 保证为正。
     * 时间序列的空档补 0 由 Java 层完成。</p>
     *
     * @param startTime 区间起点（含），不可为 null
     * @param endTime   区间终点（不含），不可为 null
     * @return 各日聚合行，无数据时返回空列表
     */
    @Select("SELECT TO_CHAR(create_time, 'YYYY-MM-DD') AS period_key, "
            + "SUM(ABS(quantity)) AS total_qty "
            + "FROM lis_reagent_stock_record "
            + "WHERE record_type = 2 AND create_time >= #{startTime} AND create_time < #{endTime} "
            + "GROUP BY TO_CHAR(create_time, 'YYYY-MM-DD') "
            + "ORDER BY period_key")
    List<Map<String, Object>> selectTrendDaily(@Param("startTime") LocalDateTime startTime,
                                               @Param("endTime") LocalDateTime endTime);

    /**
     * 领用趋势（T-18 / R-11）：按周聚合 {@code record_type = 2}（领用出库）数量。
     *
     * <p>周桶取所在周的周一（与 PostgreSQL {@code date_trunc('week', ...)} 语义一致）。</p>
     *
     * @param startTime 区间起点（含），不可为 null
     * @param endTime   区间终点（不含），不可为 null
     * @return 各周聚合行，无数据时返回空列表
     */
    @Select("SELECT TO_CHAR(DATE_TRUNC('week', create_time), 'YYYY-MM-DD') AS period_key, "
            + "SUM(ABS(quantity)) AS total_qty "
            + "FROM lis_reagent_stock_record "
            + "WHERE record_type = 2 AND create_time >= #{startTime} AND create_time < #{endTime} "
            + "GROUP BY DATE_TRUNC('week', create_time) "
            + "ORDER BY period_key")
    List<Map<String, Object>> selectTrendWeekly(@Param("startTime") LocalDateTime startTime,
                                                @Param("endTime") LocalDateTime endTime);

    /**
     * 领用趋势（T-18 / R-11）：按月聚合 {@code record_type = 2}（领用出库）数量。
     *
     * @param startTime 区间起点（含），不可为 null
     * @param endTime   区间终点（不含），不可为 null
     * @return 各月聚合行（键为当月首日），无数据时返回空列表
     */
    @Select("SELECT TO_CHAR(DATE_TRUNC('month', create_time), 'YYYY-MM-DD') AS period_key, "
            + "SUM(ABS(quantity)) AS total_qty "
            + "FROM lis_reagent_stock_record "
            + "WHERE record_type = 2 AND create_time >= #{startTime} AND create_time < #{endTime} "
            + "GROUP BY DATE_TRUNC('month', create_time) "
            + "ORDER BY period_key")
    List<Map<String, Object>> selectTrendMonthly(@Param("startTime") LocalDateTime startTime,
                                                 @Param("endTime") LocalDateTime endTime);
}
