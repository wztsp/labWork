package com.lis.reagent.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 试剂统计报表 Mapper（纯注解 @Select）。
 *
 * <p>金额口径与成本核算一致：数量 × {@code lis_reagent.price_purchase}，避免流水表
 * 本身无单价列时无法出金额。所有聚合列均用 COALESCE 兜底 0，空值不会透传给前端。</p>
 *
 * <p>流水类型口径见 {@code lis_reagent_stock_record.record_type}：
 * 1入库 2出库(领用) 3报损/报废 4退库 5盘点调账 6消耗 7调拨 8借用 9归还。</p>
 */
@Mapper
public interface ReagentStatMapper {

    /**
     * 出入库台账（明细），按时间倒序。
     *
     * <p>数据源 {@code lis_reagent_stock_record}，左连 {@code lis_reagent} 带出试剂名称、
     * 编码与采购单价；金额 = ABS(数量) × 采购单价。</p>
     */
    @Select("SELECT r.id, r.stock_id AS \"stockId\", r.reagent_id AS \"reagentId\", "
            + "rg.reagent_code AS \"reagentCode\", rg.reagent_name AS \"reagentName\", "
            + "r.record_type AS \"recordType\", "
            + "CASE r.record_type WHEN 1 THEN '入库' WHEN 2 THEN '出库(领用)' WHEN 3 THEN '报损/报废' "
            + "WHEN 4 THEN '退库' WHEN 5 THEN '盘点调账' WHEN 6 THEN '消耗' WHEN 7 THEN '调拨' "
            + "WHEN 8 THEN '借用' WHEN 9 THEN '归还' ELSE '其他' END AS \"recordTypeName\", "
            + "r.quantity, r.before_qty AS \"beforeQty\", r.after_qty AS \"afterQty\", "
            + "rg.price_purchase AS \"unitPrice\", "
            + "ROUND(ABS(r.quantity) * COALESCE(rg.price_purchase, 0), 2) AS \"amount\", "
            + "r.dept_id AS \"deptId\", COALESCE(r.dept_name, '未归属科室') AS \"deptName\", "
            + "r.operator_name AS \"operatorName\", r.source_no AS \"sourceNo\", "
            + "r.related_id AS \"relatedId\", r.related_type AS \"relatedType\", "
            + "r.remark, r.create_time AS \"createTime\" "
            + "FROM lis_reagent_stock_record r "
            + "LEFT JOIN lis_reagent rg ON rg.id = r.reagent_id "
            + "WHERE r.create_time >= #{startTime} AND r.create_time < #{endTime} "
            + "AND (#{deptId,jdbcType=BIGINT} IS NULL OR r.dept_id = #{deptId,jdbcType=BIGINT}) "
            + "AND (#{reagentId,jdbcType=BIGINT} IS NULL OR r.reagent_id = #{reagentId,jdbcType=BIGINT}) "
            + "ORDER BY r.create_time DESC, r.id DESC")
    List<Map<String, Object>> selectLedger(@Param("startTime") LocalDateTime startTime,
                                           @Param("endTime") LocalDateTime endTime,
                                           @Param("deptId") Long deptId,
                                           @Param("reagentId") Long reagentId);

    /**
     * 出入库台账汇总：正常入库(1)、偿还入库(9)、正常出库(2)、借用出库(8) 四类数量与金额。
     */
    @Select("SELECT "
            + "COALESCE(SUM(CASE WHEN r.record_type = 1 THEN ABS(r.quantity) ELSE 0 END), 0) AS \"normalInQty\", "
            + "COALESCE(SUM(CASE WHEN r.record_type = 1 THEN ABS(r.quantity) * COALESCE(rg.price_purchase, 0) ELSE 0 END), 0) AS \"normalInAmount\", "
            + "COALESCE(SUM(CASE WHEN r.record_type = 9 THEN ABS(r.quantity) ELSE 0 END), 0) AS \"repayInQty\", "
            + "COALESCE(SUM(CASE WHEN r.record_type = 9 THEN ABS(r.quantity) * COALESCE(rg.price_purchase, 0) ELSE 0 END), 0) AS \"repayInAmount\", "
            + "COALESCE(SUM(CASE WHEN r.record_type = 2 THEN ABS(r.quantity) ELSE 0 END), 0) AS \"normalOutQty\", "
            + "COALESCE(SUM(CASE WHEN r.record_type = 2 THEN ABS(r.quantity) * COALESCE(rg.price_purchase, 0) ELSE 0 END), 0) AS \"normalOutAmount\", "
            + "COALESCE(SUM(CASE WHEN r.record_type = 8 THEN ABS(r.quantity) ELSE 0 END), 0) AS \"borrowOutQty\", "
            + "COALESCE(SUM(CASE WHEN r.record_type = 8 THEN ABS(r.quantity) * COALESCE(rg.price_purchase, 0) ELSE 0 END), 0) AS \"borrowOutAmount\" "
            + "FROM lis_reagent_stock_record r "
            + "LEFT JOIN lis_reagent rg ON rg.id = r.reagent_id "
            + "WHERE r.create_time >= #{startTime} AND r.create_time < #{endTime} "
            + "AND (#{deptId,jdbcType=BIGINT} IS NULL OR r.dept_id = #{deptId,jdbcType=BIGINT})")
    Map<String, Object> selectLedgerSummary(@Param("startTime") LocalDateTime startTime,
                                            @Param("endTime") LocalDateTime endTime,
                                            @Param("deptId") Long deptId);

    /**
     * 供应商应付：已验收入库单（{@code verify_status = 1}）按供应商汇总入库金额，以验收时间为准。
     */
    @Select("SELECT COALESCE(i.supplier_id, 0) AS \"supplierId\", "
            + "COALESCE(NULLIF(s.supplier_name, ''), NULLIF(i.supplier, ''), '未知供应商') AS \"supplierName\", "
            + "CAST(COUNT(1) AS INTEGER) AS \"inboundCount\", "
            + "COALESCE(SUM(i.total_amount), 0) AS \"totalAmount\" "
            + "FROM lis_reagent_inbound i "
            + "LEFT JOIN lis_supplier s ON s.id = i.supplier_id "
            + "WHERE i.verify_status = 1 "
            + "AND i.verify_time >= #{startTime} AND i.verify_time < #{endTime} "
            + "GROUP BY i.supplier_id, s.supplier_name, i.supplier "
            + "ORDER BY COALESCE(SUM(i.total_amount), 0) DESC")
    List<Map<String, Object>> selectSupplierPayable(@Param("startTime") LocalDateTime startTime,
                                                    @Param("endTime") LocalDateTime endTime);

    /**
     * 科室/小组领用出库汇总：流水类型 2出库(领用) / 3报损 / 8借用，以流水创建时间为准。
     */
    @Select("SELECT COALESCE(r.dept_id, 0) AS \"deptId\", "
            + "COALESCE(NULLIF(r.dept_name, ''), '未归属科室') AS \"deptName\", "
            + "COALESCE(SUM(ABS(r.quantity)), 0) AS \"totalQty\", "
            + "COALESCE(SUM(ABS(r.quantity) * COALESCE(rg.price_purchase, 0)), 0) AS \"totalAmount\", "
            + "CAST(COUNT(1) AS INTEGER) AS \"recordCount\" "
            + "FROM lis_reagent_stock_record r "
            + "LEFT JOIN lis_reagent rg ON rg.id = r.reagent_id "
            + "WHERE r.record_type IN (2, 3, 8) "
            + "AND r.create_time >= #{startTime} AND r.create_time < #{endTime} "
            + "GROUP BY r.dept_id, r.dept_name "
            + "ORDER BY COALESCE(SUM(ABS(r.quantity)), 0) DESC")
    List<Map<String, Object>> selectDeptIssue(@Param("startTime") LocalDateTime startTime,
                                              @Param("endTime") LocalDateTime endTime);

    /**
     * 发票汇总：仅已登记（{@code status = 1}），按供应商汇总价税合计、税额与张数，以开票日期为准。
     */
    @Select("SELECT COALESCE(v.supplier_id, 0) AS \"supplierId\", "
            + "COALESCE(NULLIF(v.supplier_name, ''), NULLIF(s.supplier_name, ''), '未知供应商') AS \"supplierName\", "
            + "CAST(COUNT(1) AS INTEGER) AS \"invoiceCount\", "
            + "COALESCE(SUM(v.amount), 0) AS \"totalAmount\", "
            + "COALESCE(SUM(v.tax_amount), 0) AS \"totalTaxAmount\" "
            + "FROM lis_reagent_invoice v "
            + "LEFT JOIN lis_supplier s ON s.id = v.supplier_id "
            + "WHERE v.status = 1 "
            + "AND v.invoice_date >= #{startDate} AND v.invoice_date <= #{endDate} "
            + "GROUP BY v.supplier_id, v.supplier_name, s.supplier_name "
            + "ORDER BY COALESCE(SUM(v.amount), 0) DESC")
    List<Map<String, Object>> selectInvoiceSummary(@Param("startDate") LocalDate startDate,
                                                   @Param("endDate") LocalDate endDate);

    /**
     * 按科室/小组入库汇总：已验收入库单，科室取自来源采购订单（{@code order_id}），以验收时间为准。
     */
    @Select("SELECT COALESCE(o.dept_id, 0) AS \"deptId\", "
            + "COALESCE(NULLIF(o.dept_name, ''), '未归属科室') AS \"deptName\", "
            + "CAST(COUNT(1) AS INTEGER) AS \"inboundCount\", "
            + "COALESCE(SUM(it.qty), 0) AS \"totalQty\", "
            + "COALESCE(SUM(i.total_amount), 0) AS \"totalAmount\" "
            + "FROM lis_reagent_inbound i "
            + "LEFT JOIN lis_reagent_purchase_order o ON o.id = i.order_id "
            + "LEFT JOIN (SELECT inbound_id, SUM(quantity) AS qty FROM lis_reagent_inbound_item GROUP BY inbound_id) it "
            + "ON it.inbound_id = i.id "
            + "WHERE i.verify_status = 1 "
            + "AND i.verify_time >= #{startTime} AND i.verify_time < #{endTime} "
            + "GROUP BY o.dept_id, o.dept_name "
            + "ORDER BY COALESCE(SUM(i.total_amount), 0) DESC")
    List<Map<String, Object>> selectInboundByDept(@Param("startTime") LocalDateTime startTime,
                                                  @Param("endTime") LocalDateTime endTime);

    /**
     * 按供应商入库汇总：已验收入库单，以验收时间为准。
     */
    @Select("SELECT COALESCE(i.supplier_id, 0) AS \"supplierId\", "
            + "COALESCE(NULLIF(s.supplier_name, ''), NULLIF(i.supplier, ''), '未知供应商') AS \"supplierName\", "
            + "CAST(COUNT(1) AS INTEGER) AS \"inboundCount\", "
            + "COALESCE(SUM(it.qty), 0) AS \"totalQty\", "
            + "COALESCE(SUM(i.total_amount), 0) AS \"totalAmount\" "
            + "FROM lis_reagent_inbound i "
            + "LEFT JOIN lis_supplier s ON s.id = i.supplier_id "
            + "LEFT JOIN (SELECT inbound_id, SUM(quantity) AS qty FROM lis_reagent_inbound_item GROUP BY inbound_id) it "
            + "ON it.inbound_id = i.id "
            + "WHERE i.verify_status = 1 "
            + "AND i.verify_time >= #{startTime} AND i.verify_time < #{endTime} "
            + "GROUP BY i.supplier_id, s.supplier_name, i.supplier "
            + "ORDER BY COALESCE(SUM(i.total_amount), 0) DESC")
    List<Map<String, Object>> selectInboundBySupplier(@Param("startTime") LocalDateTime startTime,
                                                      @Param("endTime") LocalDateTime endTime);

    /**
     * 按小组出库汇总：出库类业务单（1领用 / 3报废 / 5借用）已审批或已执行，
     * 以业务单审核时间 {@code approve_time} 为准；免审批类型无审核时间时
     * 回退到执行时间、创建时间，避免漏统计。金额取明细金额。
     */
    @Select("SELECT COALESCE(r.dept_id, 0) AS \"deptId\", "
            + "COALESCE(NULLIF(r.dept_name, ''), '未归属科室') AS \"deptName\", "
            + "CAST(COUNT(DISTINCT r.id) AS INTEGER) AS \"requisitionCount\", "
            + "COALESCE(SUM(ri.quantity), 0) AS \"totalQty\", "
            + "COALESCE(SUM(ri.amount), 0) AS \"totalAmount\" "
            + "FROM lis_reagent_requisition r "
            + "JOIN lis_reagent_requisition_item ri ON ri.req_id = r.id "
            + "WHERE r.req_type IN (1, 3, 5) AND r.status IN (2, 3) "
            + "AND COALESCE(r.approve_time, r.execute_time, r.create_time) >= #{startTime} "
            + "AND COALESCE(r.approve_time, r.execute_time, r.create_time) < #{endTime} "
            + "GROUP BY r.dept_id, r.dept_name "
            + "ORDER BY COALESCE(SUM(ri.quantity), 0) DESC")
    List<Map<String, Object>> selectOutboundByDept(@Param("startTime") LocalDateTime startTime,
                                                   @Param("endTime") LocalDateTime endTime);

    /**
     * 实际消耗统计：按仪器汇总消耗数量与项目数（{@code lis_reagent_consumption.instrument_id / test_count}）。
     */
    @Select("SELECT COALESCE(c.instrument_id, 0) AS \"instrumentId\", "
            + "COALESCE(NULLIF(i.instrument_name, ''), '未绑定仪器') AS \"instrumentName\", "
            + "COALESCE(SUM(c.quantity), 0) AS \"totalQty\", "
            + "COALESCE(SUM(c.test_count), 0) AS \"totalTestCount\", "
            + "CAST(COUNT(1) AS INTEGER) AS \"recordCount\" "
            + "FROM lis_reagent_consumption c "
            + "LEFT JOIN lis_instrument i ON i.id = c.instrument_id "
            + "WHERE c.create_time >= #{startTime} AND c.create_time < #{endTime} "
            + "AND (#{reagentId,jdbcType=BIGINT} IS NULL OR c.reagent_id = #{reagentId,jdbcType=BIGINT}) "
            + "GROUP BY c.instrument_id, i.instrument_name "
            + "ORDER BY COALESCE(SUM(c.quantity), 0) DESC")
    List<Map<String, Object>> selectConsumptionStat(@Param("startTime") LocalDateTime startTime,
                                                    @Param("endTime") LocalDateTime endTime,
                                                    @Param("reagentId") Long reagentId);
}
