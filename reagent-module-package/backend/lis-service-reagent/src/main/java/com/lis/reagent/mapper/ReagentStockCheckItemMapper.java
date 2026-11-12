package com.lis.reagent.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.lis.reagent.entity.ReagentStockCheckItem;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.math.BigDecimal;

@Mapper
public interface ReagentStockCheckItemMapper extends BaseMapper<ReagentStockCheckItem> {

    /**
     * 汇总某次盘点的差异数量合计（盘盈为正、盘亏为负，正负相抵）。
     *
     * <p>仅统计已录入实盘数的明细（{@code actual_qty IS NOT NULL}），未盘明细不参与对账。</p>
     *
     * @param checkId 盘点单 ID，不可为 null
     * @return 差异数量合计，无数据时返回 0
     */
    @Select("SELECT COALESCE(SUM(diff_qty), 0) FROM lis_reagent_stock_check_item "
            + "WHERE check_id = #{checkId} AND actual_qty IS NOT NULL")
    BigDecimal sumDiffByCheck(@Param("checkId") Long checkId);

    /**
     * 统计某次盘点中存在差异（盘盈或盘亏）的明细条数。
     *
     * @param checkId 盘点单 ID，不可为 null
     * @return 差异明细条数，无差异时返回 0
     */
    @Select("SELECT COUNT(1) FROM lis_reagent_stock_check_item "
            + "WHERE check_id = #{checkId} AND actual_qty IS NOT NULL AND diff_qty <> 0")
    int countDiffByCheck(@Param("checkId") Long checkId);

    /**
     * 统计某次盘点中尚未录入实盘数的明细条数，用于判定盘点单能否提交。
     *
     * @param checkId 盘点单 ID，不可为 null
     * @return 未盘明细条数，0 表示已全部盘完
     */
    @Select("SELECT COUNT(1) FROM lis_reagent_stock_check_item "
            + "WHERE check_id = #{checkId} AND actual_qty IS NULL")
    int countUncheckedByCheck(@Param("checkId") Long checkId);
}
