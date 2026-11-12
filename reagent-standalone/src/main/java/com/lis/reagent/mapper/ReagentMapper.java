package com.lis.reagent.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.lis.reagent.entity.Reagent;
import com.lis.reagent.vo.LowStockAlertDTO;
import com.lis.reagent.vo.OverStockAlertDTO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 试剂字典 Mapper。
 *
 * <p>{@link #selectLowStock()} 是缺陷 I 的修复落点：用一条聚合 SQL 替代原先
 * 「循环查库存 + 拼串伪造实体」的 N+1 实现，结果映射到专用 DTO
 * {@link LowStockAlertDTO}（依赖全局 {@code map-underscore-to-camel-case: true}）。</p>
 *
 * @author LIS Team
 */
@Mapper
public interface ReagentMapper extends BaseMapper<Reagent> {

    /**
     * 低库存预警聚合查询。
     *
     * <p>判定口径（主理人 Q4 裁决）：以 {@code min_stock} 为准，所有可用批次
     * （{@code lis_reagent_stock.status = 1}）数量求和 &lt; {@code min_stock} 即告警。
     * 与 {@code lis_reagent_budget_record} 类似，金额/数量比较一律走 SQL 数值语义。
     * {@code status = 1} 对应 {@link com.lis.reagent.enums.StockStatus#AVAILABLE}，
     * 此处为 SQL 层常量，与 Java 枚举保持同步（禁止散落魔法数字约束作用于 Java 代码）。</p>
     *
     * <p>结果按缺口（minStock - 当前量）降序，最缺的排最前。</p>
     *
     * @param minStockThreshold 可选过滤阈值：仅返回缺口 ≥ 该值的试剂；null 时不过滤
     * @return 低库存告警列表，无告警时返回空列表，恒不为 null
     */
    @Select("SELECT r.id AS reagent_id, r.reagent_code, r.reagent_name, r.specification, r.unit, "
            + "       r.category_id, r.hazard_flag, r.min_stock, "
            + "       COALESCE(s.total_qty, 0) AS current_qty, "
            + "       r.min_stock - COALESCE(s.total_qty, 0) AS gap_qty, "
            + "       CASE WHEN COALESCE(s.total_qty, 0) < r.min_stock "
            + "            THEN r.min_stock - COALESCE(s.total_qty, 0) ELSE 0 END AS suggest_qty "
            + "  FROM lis_reagent r "
            + "  LEFT JOIN (SELECT reagent_id, SUM(quantity) AS total_qty "
            + "               FROM lis_reagent_stock WHERE status = 1 GROUP BY reagent_id) s "
            + "    ON s.reagent_id = r.id "
            + " WHERE r.status = 1 AND r.min_stock IS NOT NULL "
            + "   AND COALESCE(s.total_qty, 0) < r.min_stock "
+ "   AND (#{minStockThreshold,jdbcType=NUMERIC} IS NULL "
+ "        OR r.min_stock - COALESCE(s.total_qty, 0) >= #{minStockThreshold,jdbcType=NUMERIC}) "
            + " ORDER BY (r.min_stock - COALESCE(s.total_qty, 0)) DESC, r.id ASC")
    List<LowStockAlertDTO> selectLowStock(@Param("minStockThreshold") java.math.BigDecimal minStockThreshold);

    /**
     * 高库存预警聚合查询：按试剂汇总可用批次数量（{@code status = 1}），
     * 超过 {@code max_stock} 的试剂即为高库存。
     *
     * <p>结果按超储量降序，超得最多的排最前。</p>
     *
     * @return 高库存告警列表，无告警时返回空列表，恒不为 null
     */
    @Select("SELECT r.id AS reagent_id, r.reagent_code, r.reagent_name, r.max_stock, "
            + "       COALESCE(s.total_qty, 0) AS current_qty, "
            + "       COALESCE(s.total_qty, 0) - r.max_stock AS excess_qty "
            + "  FROM lis_reagent r "
            + "  JOIN (SELECT reagent_id, SUM(quantity) AS total_qty "
            + "          FROM lis_reagent_stock WHERE status = 1 GROUP BY reagent_id) s "
            + "    ON s.reagent_id = r.id "
            + " WHERE r.status = 1 AND r.max_stock IS NOT NULL "
            + "   AND COALESCE(s.total_qty, 0) > r.max_stock "
            + " ORDER BY (COALESCE(s.total_qty, 0) - r.max_stock) DESC, r.id ASC")
    List<OverStockAlertDTO> selectOverStock();
}
