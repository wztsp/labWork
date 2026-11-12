package com.lis.reagent.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.lis.reagent.entity.ReagentInboundItem;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 入库单明细 Mapper。
 *
 * <p><b>偏差说明</b>：设计文档 T-05 将 {@code arrived_qty} 条件 UPDATE（防超收兜底）
 * 挂在本接口下，但 {@code arrived_qty} / {@code quantity} 两列归属
 * {@code lis_reagent_purchase_order_item} 表，本表并无 {@code arrived_qty} 列。
 * 该方法已落位到 {@link ReagentPurchaseOrderItemMapper#receiveQty(Long, java.math.BigDecimal)}，
 * 语义与验收口径不变。</p>
 */
@Mapper
public interface ReagentInboundItemMapper extends BaseMapper<ReagentInboundItem> {

    /**
     * 取某入库单下全部明细。
     *
     * @param inboundId 入库单 ID，不可为 null
     * @return 明细列表，无数据时返回空列表
     */
    @Select("SELECT * FROM lis_reagent_inbound_item WHERE inbound_id = #{inboundId} ORDER BY id ASC")
    List<ReagentInboundItem> selectByInboundId(@Param("inboundId") Long inboundId);
}
