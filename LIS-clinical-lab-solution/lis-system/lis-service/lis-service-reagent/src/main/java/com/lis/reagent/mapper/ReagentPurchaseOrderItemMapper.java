package com.lis.reagent.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.lis.reagent.entity.ReagentPurchaseOrderItem;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

/**
 * 采购订单明细 Mapper（含到货数量条件回写）。
 *
 * <p><b>设计说明（偏差记录）</b>：设计文档 T-05 将 {@code arrived_qty} 条件 UPDATE 写在
 * {@code ReagentInboundItemMapper} 名下，但 {@code arrived_qty} / {@code quantity} 两列
 * 实际归属 {@code lis_reagent_purchase_order_item} 表（{@code lis_reagent_inbound_item}
 * 无 {@code arrived_qty} 列）。为遵循「Mapper 只操作自己 @TableName 对应的表」原则，
 * 该方法落位于本接口，语义与验收口径完全一致。</p>
 *
 * @author LIS Team
 */
@Mapper
public interface ReagentPurchaseOrderItemMapper extends BaseMapper<ReagentPurchaseOrderItem> {

    /**
     * 到货验收时精确回写累计到货量，并在 SQL 层兜底防超收。
     *
     * <p>条件 UPDATE（CAS 语义）：只有当「已到货量 + 本次到货量 ≤ 订单量」时才更新成功。
     * 返回 0 表示本次到货会导致超收，调用方必须据此抛出业务异常并回滚事务，
     * 不得忽略返回值。该写法同时天然抵御并发重复验收。</p>
     *
     * @param id  订单明细 ID，不可为 null
     * @param qty 本次到货数量，必须为正数，不可为 null
     * @return 受影响行数：1=回写成功，0=超收被拒或明细不存在
     */
    @Update("UPDATE lis_reagent_purchase_order_item "
            + "SET arrived_qty = COALESCE(arrived_qty, 0) + #{qty} "
            + "WHERE id = #{id} AND COALESCE(arrived_qty, 0) + #{qty} <= quantity")
    int receiveQty(@Param("id") Long id, @Param("qty") java.math.BigDecimal qty);

    /**
     * 冲销到货量（验收撤销 / 红冲场景），同样带条件兜底防止扣成负数。
     *
     * @param id  订单明细 ID，不可为 null
     * @param qty 冲销数量，必须为正数，不可为 null
     * @return 受影响行数：1=冲销成功，0=冲销量超过已到货量或明细不存在
     */
    @Update("UPDATE lis_reagent_purchase_order_item "
            + "SET arrived_qty = COALESCE(arrived_qty, 0) - #{qty} "
            + "WHERE id = #{id} AND COALESCE(arrived_qty, 0) >= #{qty}")
    int revertQty(@Param("id") Long id, @Param("qty") java.math.BigDecimal qty);

    /**
     * 取某订单下全部明细。
     *
     * @param orderId 订单 ID，不可为 null
     * @return 明细列表，无数据时返回空列表
     */
    @Select("SELECT * FROM lis_reagent_purchase_order_item WHERE order_id = #{orderId} ORDER BY id ASC")
    List<ReagentPurchaseOrderItem> selectByOrderId(@Param("orderId") Long orderId);

    /**
     * 统计某订单下尚未到齐的明细数量，用于判定订单是否可置为「已到货」。
     *
     * @param orderId 订单 ID，不可为 null
     * @return 未到齐明细数，0 表示已全部到齐
     */
    @Select("SELECT COUNT(1) FROM lis_reagent_purchase_order_item "
            + "WHERE order_id = #{orderId} AND COALESCE(arrived_qty, 0) < quantity")
    int countUnreceivedByOrderId(@Param("orderId") Long orderId);
}
