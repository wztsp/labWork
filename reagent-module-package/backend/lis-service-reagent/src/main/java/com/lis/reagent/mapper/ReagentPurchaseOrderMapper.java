package com.lis.reagent.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.lis.reagent.entity.ReagentPurchaseOrder;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 采购订单 Mapper。
 *
 * @author LIS Team
 */
@Mapper
public interface ReagentPurchaseOrderMapper extends BaseMapper<ReagentPurchaseOrder> {

    /**
     * 按申请单取其下全部采购订单（一张申请可拆多张订单给不同供应商）。
     *
     * @param applyId 申请单 ID，不可为 null
     * @return 订单列表，无数据时返回空列表
     */
    @Select("SELECT * FROM lis_reagent_purchase_order WHERE apply_id = #{applyId} ORDER BY id ASC")
    List<ReagentPurchaseOrder> selectByApplyId(@Param("applyId") Long applyId);

    /**
     * 统计某申请单下未全部到货的订单数量，用于判定申请单能否结案。
     *
     * <p>口径（状态码见 {@link com.lis.reagent.enums.OrderStatus}：
     * {@link com.lis.reagent.enums.OrderStatus#PENDING_ORDER}(0) 待下单 /
     * {@link com.lis.reagent.enums.OrderStatus#PLACED}(1) 已下单 /
     * {@link com.lis.reagent.enums.OrderStatus#PARTIAL_ARRIVED}(2) 部分到货 /
     * {@link com.lis.reagent.enums.OrderStatus#ARRIVED}(3) 已到货 /
     * {@link com.lis.reagent.enums.OrderStatus#CANCELLED}(4) 已取消）：
     * 排除 {@link com.lis.reagent.enums.OrderStatus#ARRIVED}(3) 与
     * {@link com.lis.reagent.enums.OrderStatus#CANCELLED}(4)，其余
     * （PENDING_ORDER 0 / PLACED 1 / PARTIAL_ARRIVED 2）均视为未完结；
     * 禁止散落魔法数字（共享知识第 14 条）。</p>
     *
     * @param applyId 申请单 ID，不可为 null
     * @return 未完结订单数，无数据时返回 0
     */
    @Select("SELECT COUNT(1) FROM lis_reagent_purchase_order "
            + "WHERE apply_id = #{applyId} AND status NOT IN (3, 4)")
    int countUnfinishedByApplyId(@Param("applyId") Long applyId);

    /**
     * 悲观行锁读取订单，用于到货验收回写订单状态，必须在同一事务内调用。
     *
     * @param id 订单 ID，不可为 null
     * @return 加锁后的订单，不存在时返回 null
     */
    @Select("SELECT * FROM lis_reagent_purchase_order WHERE id = #{id} FOR UPDATE")
    ReagentPurchaseOrder selectByIdForUpdate(@Param("id") Long id);

    /**
     * 取当日同前缀的最大单号，供 {@code com.lis.reagent.support.BizNoGenerator} 续号。
     *
     * @param head 前缀 + yyyyMMdd 头部（如 {@code PUR20260804}），不可为 null
     * @return 最大单号；当日尚无单据时返回 null
     */
    @Select("SELECT MAX(order_no) FROM lis_reagent_purchase_order WHERE order_no LIKE #{head} || '%'")
    String selectMaxNo(@Param("head") String head);

    /**
     * 供应商准时率统计（T-16 / R-11）：按供应商分组统计已下单/部分到货/已到货
     * （status IN (1, 2, 3)）的订单总数、准时订单数与最近下单日。
     *
     * <p>准时定义：{@code expect_date} 为空视为准时，否则 {@code order_date}
     * 不晚于 {@code expect_date} 视为准时。结果行含
     * {@code supplier_id / total_count / on_time_count / last_order_date}。</p>
     *
     * @return 各供应商的准时率统计行，无数据时返回空列表
     */
    @Select("SELECT supplier_id AS supplier_id, "
            + "COUNT(1) AS total_count, "
            + "SUM(CASE WHEN expect_date IS NULL OR order_date <= expect_date THEN 1 ELSE 0 END) AS on_time_count, "
            + "MAX(order_date) AS last_order_date "
            + "FROM lis_reagent_purchase_order "
            + "WHERE status IN (1, 2, 3) "
            + "GROUP BY supplier_id")
    List<Map<String, Object>> selectOnTimeStatBySupplier();

    /**
     * 采购统计（T-18 / R-11）：按供应商分组统计订单数、总金额、到货订单数。
     *
     * <p>过滤区间为 {@code [startTime, endTime)}（左闭右开）；到货订单以
     * {@code status = 3}（已到货）判定。</p>
     *
     * @param startTime 区间起点（含），不可为 null
     * @param endTime   区间终点（不含），不可为 null
     * @return 各供应商统计行，无数据时返回空列表
     */
    @Select("SELECT supplier_id AS supplier_id, supplier_name AS supplier_name, "
            + "COUNT(1) AS order_count, "
            + "COALESCE(SUM(total_amount), 0) AS total_amount, "
            + "SUM(CASE WHEN status = 3 THEN 1 ELSE 0 END) AS arrived_order_count "
            + "FROM lis_reagent_purchase_order "
            + "WHERE create_time >= #{startTime} AND create_time < #{endTime} "
            + "GROUP BY supplier_id, supplier_name "
            + "ORDER BY total_amount DESC")
    List<Map<String, Object>> selectPurchaseStatBySupplier(@Param("startTime") LocalDateTime startTime,
                                                           @Param("endTime") LocalDateTime endTime);

    /**
     * 采购统计（T-18 / R-11）：按科室分组统计订单数、总金额、到货订单数。
     *
     * @param startTime 区间起点（含），不可为 null
     * @param endTime   区间终点（不含），不可为 null
     * @return 各科室统计行，无数据时返回空列表
     */
    @Select("SELECT dept_id AS dept_id, dept_name AS dept_name, "
            + "COUNT(1) AS order_count, "
            + "COALESCE(SUM(total_amount), 0) AS total_amount, "
            + "SUM(CASE WHEN status = 3 THEN 1 ELSE 0 END) AS arrived_order_count "
            + "FROM lis_reagent_purchase_order "
            + "WHERE create_time >= #{startTime} AND create_time < #{endTime} "
            + "GROUP BY dept_id, dept_name "
            + "ORDER BY total_amount DESC")
    List<Map<String, Object>> selectPurchaseStatByDept(@Param("startTime") LocalDateTime startTime,
                                                       @Param("endTime") LocalDateTime endTime);

    /**
     * 采购统计（T-18 / R-11）：按试剂分类分组统计订单数、总金额、到货订单数。
     *
     * <p>一张订单可含多个分类的明细，故订单数用 {@code COUNT(DISTINCT o.id)} 去重、
     * 金额按明细行金额 {@code SUM(oi.amount)} 归集、到货订单数用
     * {@code COUNT(DISTINCT CASE WHEN o.status = 3 THEN o.id END)} 判定。</p>
     *
     * @param startTime 区间起点（含），不可为 null
     * @param endTime   区间终点（不含），不可为 null
     * @return 各分类统计行，无数据时返回空列表
     */
    @Select("SELECT r.category_id AS category_id, "
            + "COALESCE(c.category_name, '') AS category_name, "
            + "COUNT(DISTINCT o.id) AS order_count, "
            + "COALESCE(SUM(oi.amount), 0) AS total_amount, "
            + "COUNT(DISTINCT CASE WHEN o.status = 3 THEN o.id END) AS arrived_order_count "
            + "FROM lis_reagent_purchase_order o "
            + "JOIN lis_reagent_purchase_order_item oi ON oi.order_id = o.id "
            + "JOIN lis_reagent r ON r.id = oi.reagent_id "
            + "LEFT JOIN lis_reagent_category c ON c.id = r.category_id "
            + "WHERE o.create_time >= #{startTime} AND o.create_time < #{endTime} "
            + "GROUP BY r.category_id, c.category_name "
            + "ORDER BY total_amount DESC")
    List<Map<String, Object>> selectPurchaseStatByCategory(@Param("startTime") LocalDateTime startTime,
                                                           @Param("endTime") LocalDateTime endTime);
}
