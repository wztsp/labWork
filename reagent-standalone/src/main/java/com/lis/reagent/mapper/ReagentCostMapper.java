package com.lis.reagent.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 试剂成本核算 Mapper
 * 基于 lis_reagent_consumption 关联 lis_reagent 计算消耗成本
 */
@Mapper
public interface ReagentCostMapper {

    /**
     * 成本汇总 (按维度分组)
     * @param dimension reagent(项目维度) / instrument(仪器维度)
     */
    default List<Map<String, Object>> summary(LocalDateTime start, LocalDateTime end, String dimension) {
        if ("instrument".equalsIgnoreCase(dimension)) {
            return summaryByInstrument(start, end);
        }
        return summaryByReagent(start, end);
    }

    @Select("<script>" +
            "SELECT c.reagent_id AS \"reagentId\", r.reagent_name AS \"reagentName\", " +
            "r.reagent_code AS \"reagentCode\", " +
            "COALESCE(SUM(c.quantity), 0) AS \"totalQty\", " +
            "COALESCE(SUM(c.quantity * r.price_purchase), 0) AS \"totalCost\" " +
            "FROM lis_reagent_consumption c " +
            "LEFT JOIN lis_reagent r ON c.reagent_id = r.id " +
            "WHERE c.create_time &gt;= #{start} AND c.create_time &lt; #{end} " +
            "GROUP BY c.reagent_id, r.reagent_name, r.reagent_code " +
            "ORDER BY \"totalCost\" DESC" +
            "</script>")
    List<Map<String, Object>> summaryByReagent(@Param("start") LocalDateTime start,
                                                @Param("end") LocalDateTime end);

    @Select("<script>" +
            "SELECT COALESCE(r.instrument_id, 0) AS \"instrumentId\", " +
            "COALESCE(i.instrument_name, '未绑定仪器') AS \"instrumentName\", " +
            "COALESCE(SUM(c.quantity), 0) AS \"totalQty\", " +
            "COALESCE(SUM(c.quantity * r.price_purchase), 0) AS \"totalCost\" " +
            "FROM lis_reagent_consumption c " +
            "LEFT JOIN lis_reagent r ON c.reagent_id = r.id " +
            "LEFT JOIN lis_instrument i ON r.instrument_id = i.id " +
            "WHERE c.create_time &gt;= #{start} AND c.create_time &lt; #{end} " +
            "GROUP BY r.instrument_id, i.instrument_name " +
            "ORDER BY \"totalCost\" DESC" +
            "</script>")
    List<Map<String, Object>> summaryByInstrument(@Param("start") LocalDateTime start,
                                                   @Param("end") LocalDateTime end);

    /**
     * 成本明细
     */
    @Select("<script>" +
            "SELECT c.id, c.consumption_no AS \"consumptionNo\", c.reagent_id AS \"reagentId\", " +
            "r.reagent_name AS \"reagentName\", c.lot_no AS \"lotNo\", c.quantity, c.unit, " +
            "c.consume_type AS \"consumeType\", c.related_no AS \"relatedNo\", " +
            "c.operator_id AS \"operatorId\", c.operator_name AS \"operatorName\", " +
            "(c.quantity * r.price_purchase) AS \"cost\", c.create_time AS \"createTime\" " +
            "FROM lis_reagent_consumption c " +
            "LEFT JOIN lis_reagent r ON c.reagent_id = r.id " +
            "WHERE c.create_time &gt;= #{start} AND c.create_time &lt; #{end} " +
            "ORDER BY \"createTime\" DESC" +
            "</script>")
    List<Map<String, Object>> detail(@Param("start") LocalDateTime start,
                                     @Param("end") LocalDateTime end);
}
