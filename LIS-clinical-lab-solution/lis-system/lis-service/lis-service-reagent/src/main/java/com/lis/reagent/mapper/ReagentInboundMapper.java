package com.lis.reagent.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.lis.reagent.entity.ReagentInbound;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;
import java.util.Map;

@Mapper
public interface ReagentInboundMapper extends BaseMapper<ReagentInbound> {

    /**
     * 按采购订单取其下全部入库单（一张订单可分批到货，对应多张入库单）。
     *
     * @param orderId 采购订单 ID，不可为 null
     * @return 入库单列表，无数据时返回空列表
     */
    @Select("SELECT * FROM lis_reagent_inbound WHERE order_id = #{orderId} ORDER BY id ASC")
    List<ReagentInbound> selectByOrderId(@Param("orderId") Long orderId);

    /**
     * 统计某采购订单已产生的入库单数量，用于订单删除 / 取消前的关联校验。
     *
     * @param orderId 采购订单 ID，不可为 null
     * @return 入库单数量，无数据时返回 0
     */
    @Select("SELECT COUNT(1) FROM lis_reagent_inbound WHERE order_id = #{orderId}")
    int countByOrderId(@Param("orderId") Long orderId);

    /**
     * 取当日同前缀的最大单号，供 {@code com.lis.reagent.support.BizNoGenerator} 续号。
     *
     * @param head 前缀 + yyyyMMdd 头部（如 {@code INB20260804}），不可为 null
     * @return 最大单号；当日尚无单据时返回 null
     */
    @Select("SELECT MAX(inbound_no) FROM lis_reagent_inbound WHERE inbound_no LIKE #{head} || '%'")
    String selectMaxNo(@Param("head") String head);

    /**
     * 供应商拒收率统计（T-16 / R-11）：按供应商分组统计入库单总数与拒收
     * （{@code verify_status = 2}）数量。结果行含
     * {@code supplier_id / total_count / reject_count}。</p>
     *
     * @return 各供应商的拒收率统计行，无数据时返回空列表
     */
    @Select("SELECT supplier_id AS supplier_id, "
            + "COUNT(1) AS total_count, "
            + "SUM(CASE WHEN verify_status = 2 THEN 1 ELSE 0 END) AS reject_count "
            + "FROM lis_reagent_inbound "
            + "GROUP BY supplier_id")
    List<Map<String, Object>> selectRejectStatBySupplier();
}
