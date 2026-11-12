package com.lis.reagent.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.lis.reagent.entity.ReagentStock;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

@Mapper
public interface ReagentStockMapper extends BaseMapper<ReagentStock> {

    /**
     * 库存结构分析（T-18 / R-11）：按试剂分类统计当前库存量与理论库存上限。
     *
     * <p>关联 {@code lis_reagent} 取分类归属，再关联 {@code lis_reagent_category} 取分类名。
     * {@code current_stock} 为各分类现有库存量合计（结构分布），
     * {@code max_stock} 为各分类下试剂 {@code max_stock} 合计（理论容量）。</p>
     *
     * @return 各分类库存结构行，无数据时返回空列表
     */
    @Select("SELECT r.category_id AS category_id, "
            + "COALESCE(c.category_name, '') AS category_name, "
            + "COALESCE(SUM(s.quantity), 0) AS current_stock, "
            + "COALESCE(SUM(r.max_stock), 0) AS max_stock "
            + "FROM lis_reagent_stock s "
            + "JOIN lis_reagent r ON r.id = s.reagent_id "
            + "LEFT JOIN lis_reagent_category c ON c.id = r.category_id "
            + "GROUP BY r.category_id, c.category_name "
            + "ORDER BY current_stock DESC")
    List<Map<String, Object>> selectStockStructure();

    /**
     * 按库存量区间筛选试剂（T-19 / R-14）：按试剂聚合各库存批次数量，
     * 命中的试剂总量落在 {@code [minQty, maxQty]} 区间内。
     *
     * <p>任一边界为 {@code null} 表示该侧不设限；两侧均 null 时返回全部有库存的试剂。</p>
     *
     * @param minQty 库存量下限，可为 null
     * @param maxQty 库存量上限，可为 null
     * @return 符合条件的试剂 ID 集合
     */
    @Select("SELECT reagent_id FROM lis_reagent_stock "
            + "GROUP BY reagent_id "
            + "HAVING (CAST(#{minQty} AS NUMERIC) IS NULL OR SUM(quantity) >= CAST(#{minQty} AS NUMERIC)) "
            + "AND (CAST(#{maxQty} AS NUMERIC) IS NULL OR SUM(quantity) <= CAST(#{maxQty} AS NUMERIC))")
    List<Long> selectReagentIdsByQuantityRange(@Param("minQty") BigDecimal minQty,
                                               @Param("maxQty") BigDecimal maxQty);
}
