package com.lis.reagent.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.lis.reagent.entity.Supplier;
import com.lis.reagent.vo.SupplierPriceVO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 供应商 Mapper（含证照临期筛选与历史比价聚合）。
 *
 * <p>结果集依赖全局配置 {@code map-underscore-to-camel-case: true} 完成
 * snake_case → camelCase 自动映射，因此所有聚合列均显式起了与
 * {@link SupplierPriceVO} 字段同名的下划线别名。</p>
 *
 * @author LIS Team
 */
@Mapper
public interface SupplierMapper extends BaseMapper<Supplier> {

    /**
     * 证照临期筛选：取在 {@code days} 天内到期（或已过期）的启用中供应商。
     *
     * <p>经营许可证与医疗器械经营许可证任一临期即命中。天数用
     * {@code CURRENT_DATE + (#{days} * INTERVAL '1 day')} 参与比较：
     * 改用乘法而非字符串拼接（{@code #{days} || ' day'}），避免 JDBC 将 {@code #{days}}
     * 推断为 unknown 类型时触发 {@code operator is not unique}（T-05 技术点 ①）。
     * {@code #{days}} 仍为预编译绑定参数。</p>
     *
     * @param days 提前预警天数（如 30），不可为 null；传 0 表示只取今天及之前已过期的
     * @return 命中的供应商列表，按最早到期日升序；无匹配时返回空列表
     */
    @Select("SELECT * FROM lis_supplier WHERE status = 1 AND ("
            + "(license_expiry IS NOT NULL AND license_expiry <= CURRENT_DATE + (#{days} * INTERVAL '1 day')) "
            + "OR (medical_cert_expiry IS NOT NULL AND medical_cert_expiry <= CURRENT_DATE + (#{days} * INTERVAL '1 day'))"
            + ") ORDER BY LEAST(COALESCE(license_expiry, DATE '9999-12-31'), "
            + "COALESCE(medical_cert_expiry, DATE '9999-12-31')) ASC, id ASC")
    List<Supplier> licenseExpiryWithinDays(@Param("days") Integer days);

    /**
     * 取全部证照有效（资质有效且两证均未过期）的启用中供应商，供采购订单下拉使用。
     *
     * <p>是 {@link #licenseExpiryWithinDays(Integer)} 的反向筛选。</p>
     *
     * @return 可选供应商列表，无匹配时返回空列表
     */
    @Select("SELECT * FROM lis_supplier WHERE status = 1 AND qual_status = 1 "
            + "AND (license_expiry IS NULL OR license_expiry > CURRENT_DATE) "
            + "AND (medical_cert_expiry IS NULL OR medical_cert_expiry > CURRENT_DATE) "
            + "ORDER BY score DESC NULLS LAST, id ASC")
    List<Supplier> selectSelectableSuppliers();

    /**
     * 历史比价聚合：按供应商维度汇总某试剂的历史成交单价。
     *
     * <p>数据源为已实际下达的采购订单明细。订单状态码见
     * {@link com.lis.reagent.enums.OrderStatus}：
     * {@link com.lis.reagent.enums.OrderStatus#PENDING_ORDER}(0) 待下单 /
     * {@link com.lis.reagent.enums.OrderStatus#PLACED}(1) 已下单 /
     * {@link com.lis.reagent.enums.OrderStatus#PARTIAL_ARRIVED}(2) 部分到货 /
     * {@link com.lis.reagent.enums.OrderStatus#ARRIVED}(3) 已到货 /
     * {@link com.lis.reagent.enums.OrderStatus#CANCELLED}(4) 已取消；
     * 比价只认已下单及以后的成交价，故排除
     * {@link com.lis.reagent.enums.OrderStatus#PENDING_ORDER}(0) 与
     * {@link com.lis.reagent.enums.OrderStatus#CANCELLED}(4)（SQL 中 {@code status NOT IN (0, 4)}）；
     * 禁止散落魔法数字（共享知识第 14 条）。
     * {@code latest_price} 用相关子查询取最近一次成交单价，
     * 其余为 MIN / MAX / AVG / COUNT / SUM 聚合。</p>
     *
     * @param reagentId 试剂 ID，不可为 null
     * @return 各供应商的价格聚合行，按平均单价升序；无历史成交时返回空列表
     */
    @Select("SELECT o.supplier_id AS supplier_id, "
            + "o.supplier_name AS supplier_name, "
            + "oi.reagent_id AS reagent_id, "
            + "MIN(oi.unit_price) AS min_price, "
            + "MAX(oi.unit_price) AS max_price, "
            + "ROUND(AVG(oi.unit_price), 4) AS avg_price, "
            + "CAST(COUNT(1) AS INTEGER) AS order_count, "
            + "COALESCE(SUM(oi.quantity), 0) AS total_quantity, "
            + "MAX(o.order_date) AS latest_order_date, "
            + "MAX(s.score) AS score, "
            + "MAX(s.qual_status) AS qual_status, "
            + "(SELECT oi2.unit_price FROM lis_reagent_purchase_order_item oi2 "
            + "  JOIN lis_reagent_purchase_order o2 ON o2.id = oi2.order_id "
            + "  WHERE o2.supplier_id = o.supplier_id AND oi2.reagent_id = oi.reagent_id "
            + "    AND o2.status NOT IN (0, 4) "
            + "  ORDER BY o2.order_date DESC NULLS LAST, o2.id DESC LIMIT 1) AS latest_price "
            + "FROM lis_reagent_purchase_order_item oi "
            + "JOIN lis_reagent_purchase_order o ON o.id = oi.order_id "
            + "LEFT JOIN lis_supplier s ON s.id = o.supplier_id "
            + "WHERE oi.reagent_id = #{reagentId} AND o.status NOT IN (0, 4) "
            + "GROUP BY o.supplier_id, o.supplier_name, oi.reagent_id "
            + "ORDER BY ROUND(AVG(oi.unit_price), 4) ASC")
    List<SupplierPriceVO> comparePriceByReagent(@Param("reagentId") Long reagentId);

    /**
     * 按规范化名称查重（R-04 验收 3）。
     *
     * <p>与 DB 唯一索引 {@code uk_lis_supplier_name_norm} 同口径：直接在 SQL 内调用
     * IMMUTABLE 函数 {@code fn_supplier_name_norm}（连续空白折叠为单空格 + BTRIM + LOWER），
     * Java 侧不做二次归一化，杜绝两端算法漂移。{@code supplier_name} 仍存原始串，
     * 展示不变小写。查询时排除自身（更新场景）。</p>
     *
     * @param name      待查重的原始供应商名称，不可为 null
     * @param excludeId 排除的供应商 ID（更新时传自身 ID），可为 null
     * @return 命中条数：0 表示无重名
     */
    @Select("SELECT COUNT(1) FROM lis_supplier "
            + "WHERE fn_supplier_name_norm(supplier_name) = fn_supplier_name_norm(#{name,jdbcType=VARCHAR}) "
            + "  AND (#{excludeId,jdbcType=BIGINT} IS NULL OR id != #{excludeId,jdbcType=BIGINT})")
    int countByNameNorm(@Param("name") String name, @Param("excludeId") Long excludeId);
}
