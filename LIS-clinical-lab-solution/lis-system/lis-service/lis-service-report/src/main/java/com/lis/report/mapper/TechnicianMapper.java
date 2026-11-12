package com.lis.report.mapper;

import org.apache.ibatis.annotations.*;

import java.util.List;
import java.util.Map;

/**
 * 技师工作站 Mapper: 直接查询标本/申请/检验项目字典表
 */
@Mapper
public interface TechnicianMapper {

    /**
     * 查询已核收的标本列表 (status >= 4), 关联患者信息, 支持核收仪器/日期/关键字/就诊类型筛选
     * 同时 UNION ALL 合并「无主标本」分组记录（lis_instrument_unmatched_result 按仪器+样本号聚合，状态=0待处理），
     * 使无主标本直接出现在技师工作站标本列表中，便于就地认领/作废处理。
     * 支持 LIMIT / OFFSET 分页: page=1 时 offset=0; size 推荐 <=5000 以保证查询性能
     */
    @Select("<script>" +
            "SELECT * FROM (" +
            "  /* ========== 第一部分：正常业务标本（已核收及以上） ========== */" +
            "  SELECT s.id, s.sample_barcode, s.sample_no, s.order_id, s.patient_id, " +
            "  s.patient_name, s.sample_type, s.status, s.is_urgent, s.department, " +
            "  s.collect_time, s.receive_time, s.test_start_time, s.report_time, " +
            "  s.instrument_id, s.operator_id, s.create_time, " +
            "  v.visit_no, v.visit_type, p.gender AS patient_gender, p.age AS patient_age, " +
            "  o.apply_dept, o.apply_doctor, o.clinical_diag, o.apply_time, " +
            "  p.phone_enc AS patient_phone, p.id_card_enc AS patient_id_card, p.address AS patient_address, " +
            "  /* 标本卡「检验项目」展示：与右侧 detail 面板的「共 N 个检验项目」对齐。" +
            "     优先使用已回传的检验结果 JOIN 项目字典 -> 提取 DISTINCT panel_name (若无 panel 则用 item_name 兜底)；" +
            "     若标本刚核收尚无结果，再降级为申请项目 oi.test_item_name，避免空展示。 */" +
            "  COALESCE((SELECT string_agg(DISTINCT COALESCE(p.item_name, t.item_name), '、' " +
            "                 ORDER BY COALESCE(p.item_name, t.item_name)) " +
            "           FROM lis_result r " +
            "           JOIN lis_test_item t ON t.id = r.test_item_id " +
            "           LEFT JOIN lis_test_item p ON p.id = t.parent_id " +
            "           WHERE r.sample_id = s.id " +
            "           HAVING COUNT(r.id) > 0), " +
            "          (SELECT string_agg(DISTINCT oi.test_item_name, '、' ORDER BY oi.test_item_name) " +
            "           FROM lis_order_item oi WHERE oi.order_id = s.order_id), " +
            "          '') AS test_items, " +
            "  false::boolean AS is_unmatched, " +
            "  NULL::text AS unmatched_sample_no, " +
            "  NULL::bigint AS unmatched_instrument_id, " +
            "  NULL::text AS unmatched_instrument_name, " +
            "  NULL::text AS unmatched_result_ids, " +
            "  NULL::integer AS unmatched_result_count, " +
            "  NULL::text AS unmatched_result_summary " +
            "  FROM lis_sample s " +
            "  LEFT JOIN lis_lab_order o ON s.order_id = o.id " +
            "  LEFT JOIN lis_visit v ON o.visit_id = v.id " +
            "  LEFT JOIN lis_patient p ON o.patient_id = p.id " +
            "  WHERE s.deleted = 0 AND s.status &gt;= 4 " +
            "  <if test='instrumentId != null'>" +
            "   AND s.instrument_id = #{instrumentId} " +
            "  </if>" +
            "  <if test='startDate != null and startDate != \"\"'>" +
            "   AND s.receive_time &gt;= #{startDate}::date " +
            "  </if>" +
            "  <if test='endDate != null and endDate != \"\"'>" +
            "   AND s.receive_time &lt;= (#{endDate}::date + interval '1 day') " +
            "  </if>" +
            "  <if test='keyword != null and keyword != \"\"'>" +
            "   AND (s.sample_barcode LIKE '%' || #{keyword} || '%' OR s.patient_name LIKE '%' || #{keyword} || '%') " +
            "  </if>" +
            "  <if test='visitType != null and visitType != \"\"'>" +
            "   AND v.visit_type = #{visitType} " +
            "  </if>" +
            "  UNION ALL " +
            "  /* ========== 第二部分：无主标本（按仪器+样本号分组聚合） ========== */" +
            "  SELECT " +
            "    -MIN(u.id) AS id, " +                 // 合成负 ID，避免与真实标本冲突
            "    NULL AS sample_barcode, " +
            "    u.sample_no AS sample_no, " +
            "    NULL AS order_id, " +
            "    NULL AS patient_id, " +
            "    '无主标本' AS patient_name, " +      // 卡片标题显示「无主标本」
            "    NULL AS sample_type, " +
            "    99 AS status, " +                    // 99 = 特殊状态：无主标本（待处理）
            "    0 AS is_urgent, " +
            "    NULL AS department, " +
            "    NULL AS collect_time, " +
            "    MIN(u.create_time) AS receive_time, " +  // 最早接收时间作为排序/核收时间
            "    NULL AS test_start_time, " +
            "    NULL AS report_time, " +
            "    u.instrument_id, " +
            "    NULL AS operator_id, " +
            "    MIN(u.create_time) AS create_time, " +
            "    NULL AS visit_no, " +
            "    NULL AS visit_type, " +
            "    NULL AS patient_gender, " +
            "    NULL AS patient_age, " +
            "    u.instrument_name AS apply_dept, " +    // 借用「开单科室」显示来源仪器名
            "    NULL AS apply_doctor, " +
            "    ('仪器回传未匹配：' || COUNT(*) || ' 项结果') AS clinical_diag, " +
            "    NULL AS apply_time, " +
            "    NULL AS patient_phone, " +
            "    NULL AS patient_id_card, " +
            "    NULL AS patient_address, " +
            "    COALESCE(string_agg(DISTINCT COALESCE(u.test_name, u.test_code), '、' ORDER BY COALESCE(u.test_name, u.test_code)), '') AS test_items, " +
            "    true::boolean AS is_unmatched, " +
            "    u.sample_no AS unmatched_sample_no, " +
            "    u.instrument_id AS unmatched_instrument_id, " +
            "    u.instrument_name AS unmatched_instrument_name, " +
            "    string_agg(DISTINCT u.id::text, ',' ORDER BY u.id::text) AS unmatched_result_ids, " +
            "    COUNT(*)::integer AS unmatched_result_count, " +
            "    string_agg(DISTINCT CONCAT(COALESCE(u.test_name, u.test_code), '=', u.result_value), ' | ') AS unmatched_result_summary " +
            "  FROM lis_instrument_unmatched_result u " +
            "  WHERE u.status = 0 " +
            "  <if test='instrumentId != null'>" +
            "   AND u.instrument_id = #{instrumentId} " +
            "  </if>" +
            "  <if test='startDate != null and startDate != \"\"'>" +
            "   AND u.create_time &gt;= #{startDate}::date " +
            "  </if>" +
            "  <if test='endDate != null and endDate != \"\"'>" +
            "   AND u.create_time &lt;= (#{endDate}::date + interval '1 day') " +
            "  </if>" +
            "  <if test='keyword != null and keyword != \"\"'>" +
            "   AND (u.sample_no LIKE '%' || #{keyword} || '%' " +
            "        OR EXISTS (SELECT 1 FROM lis_instrument_unmatched_result u2 " +
            "                   WHERE u2.instrument_id = u.instrument_id AND u2.sample_no = u.sample_no AND u2.status = 0 " +
            "                   AND (u2.test_name LIKE '%' || #{keyword} || '%' OR u2.test_code LIKE '%' || #{keyword} || '%'))) " +
            "  </if>" +
            "  GROUP BY u.instrument_id, u.instrument_name, u.sample_no " +
            ") combined " +
            "ORDER BY combined.receive_time DESC NULLS LAST " +
            "LIMIT #{size} OFFSET #{offset}" +
            "</script>")
    List<Map<String, Object>> selectReceivedSamples(
            @Param("keyword") String keyword,
            @Param("visitType") String visitType,
            @Param("startDate") String startDate,
            @Param("endDate") String endDate,
            @Param("offset") int offset,
            @Param("size") int size,
            @Param("instrumentId") Long instrumentId);

    /**
     * 统计汇总: 按核收仪器/日期筛选, 计算总数/待审/已审/急诊数量。
     * 待审 = status 4/5 (已核收/检测中), 已审 = status &gt;= 6 (已报告/终审), 急诊 = is_urgent = 1
     */
    @Select("<script>" +
            "SELECT COUNT(*) AS total, " +
            "COUNT(*) FILTER (WHERE s.status &gt;= 6) AS audited, " +
            "COUNT(*) FILTER (WHERE s.status &lt; 6) AS pending, " +
            "COUNT(*) FILTER (WHERE s.is_urgent = 1) AS urgent " +
            "FROM lis_sample s " +
            "WHERE s.deleted = 0 AND s.status &gt;= 4 " +
            "<if test='instrumentId != null'>" +
            " AND s.instrument_id = #{instrumentId} " +
            "</if>" +
            "<if test='startDate != null and startDate != \"\"'>" +
            " AND s.receive_time &gt;= #{startDate}::date " +
            "</if>" +
            "<if test='endDate != null and endDate != \"\"'>" +
            " AND s.receive_time &lt;= (#{endDate}::date + interval '1 day') " +
            "</if>" +
            "</script>")
    Map<String, Object> selectSummaryStats(
            @Param("instrumentId") Long instrumentId,
            @Param("startDate") String startDate,
            @Param("endDate") String endDate);

    /**
     * 查询标本详情 (含患者信息 + 采集/签收/检验/审核人员)
     */
    @Select("SELECT s.id, s.sample_barcode, s.sample_no, s.order_id, s.patient_id, " +
            "s.patient_name, s.sample_type, s.container_type, s.status, s.is_urgent, " +
            "s.department, s.collect_time, s.receive_time, s.test_start_time, s.report_time, " +
            "s.instrument_id, s.operator_id, " +
            /* === 签收人：优先 s.receiver + s.receiver_id，回退到核收操作人 === */
            "COALESCE(NULLIF(s.receiver, ''), st_receiver.name) AS receiver, " +
            "COALESCE(s.receiver_id, s.operator_id) AS receiver_id, " +
            /* === 采集人：s.collector_id -> staff 表查姓名 === */
            "st_collector.name AS collector_name, s.collector_id, " +
            /* === 检验人：标本表 inspector + inspector_user_id === */
            "s.inspector, s.inspector_user_id, " +
            /* === 审核人：lis_report 表 auditor_id -> staff 表查姓名 === */
            "rpt.auditor_id AS report_auditor_id, st_auditor.name AS report_auditor_name, " +
            "rpt.audit_time AS report_audit_time, " +
            "s.remark, s.create_time, " +
            "v.visit_no, v.visit_type, p.gender AS patient_gender, p.age AS patient_age, " +
            "o.apply_dept, o.apply_doctor, o.clinical_diag, o.apply_time, " +
            "p.phone_enc AS patient_phone, p.id_card_enc AS patient_id_card, p.address AS patient_address, " +
            "p.name AS order_patient_name " +
            "FROM lis_sample s " +
            "LEFT JOIN lis_lab_order o ON s.order_id = o.id " +
            "LEFT JOIN lis_visit v ON o.visit_id = v.id " +
            "LEFT JOIN lis_patient p ON o.patient_id = p.id " +
            "LEFT JOIN lis_staff st_receiver ON st_receiver.id = COALESCE(s.receiver_id, s.operator_id) " +
            "LEFT JOIN lis_staff st_collector ON st_collector.id = s.collector_id " +
            "LEFT JOIN lis_report rpt ON rpt.sample_id = s.id " +
            "LEFT JOIN lis_staff st_auditor ON st_auditor.id = rpt.auditor_id " +
            "WHERE s.id = #{sampleId} AND s.deleted = 0 " +
            "ORDER BY rpt.id DESC LIMIT 1")
    Map<String, Object> selectSampleDetail(@Param("sampleId") Long sampleId);

    /**
     * 查询标本关联的图像列表 (标本级, 一个标本对应多个图像, 血常规/尿常规等形态图像)
     * 关联表: lis_sample_image
     */
    @Select("SELECT id, sample_id AS \"sampleId\", image_url AS \"imageUrl\", " +
            "image_name AS \"imageName\", image_type AS \"imageType\", sort_no AS \"sortNo\" " +
            "FROM lis_sample_image " +
            "WHERE sample_id = #{sampleId} " +
            "ORDER BY sort_no ASC, id ASC")
    List<Map<String, Object>> selectSampleImages(@Param("sampleId") Long sampleId);

    /**
     * 查询报告签名信息 (检验人/报告医生), 用于报告打印时手写签名图片匹配
     */
    @Select("SELECT checker, report_doctor FROM lis_report " +
            "WHERE sample_id = #{sampleId} " +
            "ORDER BY id DESC LIMIT 1")
    Map<String, Object> selectReportSignInfo(@Param("sampleId") Long sampleId);

    /**
     * 查询标本关联的检验项目 (通过 lis_sample_order_item 中间表 -> lis_order_item -> lis_test_item)
     * 返回的是检验项目 (is_panel=1) 列表
     */
    @Select("SELECT DISTINCT oi.test_item_id, oi.test_item_code, oi.test_item_name " +
            "FROM lis_sample_order_item soi " +
            "JOIN lis_order_item oi ON soi.order_item_id = oi.id " +
            "WHERE soi.sample_id = #{sampleId} " +
            "ORDER BY oi.test_item_id")
    List<Map<String, Object>> selectOrderItemsBySampleId(@Param("sampleId") Long sampleId);

    /**
     * 降级方案: 当标本未正确写入 lis_sample_order_item 时, 通过 order_id 直接查 lis_order_item
     * (多数业务场景下申请单->lis_order_item 是始终存在的可靠链路)
     */
    @Select("SELECT DISTINCT oi.test_item_id, oi.test_item_code, oi.test_item_name " +
            "FROM lis_order_item oi " +
            "WHERE oi.order_id = #{orderId} " +
            "ORDER BY oi.test_item_id")
    List<Map<String, Object>> selectOrderItemsByOrderId(@Param("orderId") Long orderId);

    /**
     * 查询检验项目下的检验指标 (is_panel=0, parent_id=检验项目ID)
     * 关联参考范围表, 按性别和年龄匹配, 每个指标只返回一条最优匹配的参考范围
     * 匹配优先级: 1.性别精确匹配 > 性别不限; 2.年龄范围最小(最精确)优先; 3.有参考值优先
     * 注意: 含 foreach 动态标签, 必须用 <script> 包裹, 否则 MyBatis 不解析动态 SQL
     */
    @Select("<script>" +
            "SELECT DISTINCT ON (t.id) " +
            "t.id AS test_item_id, t.item_code, t.item_name, t.item_name_en, " +
            "t.unit, t.category, t.parent_id, " +
            "p.item_name AS panel_name, " +
            "rr.ref_low, rr.ref_high, rr.ref_text, " +
            "rr.critical_low, rr.critical_high " +
            "FROM lis_test_item t " +
            "LEFT JOIN lis_test_item p ON t.parent_id = p.id " +
            "LEFT JOIN lis_reference_range rr ON rr.test_item_id = t.id " +
            "AND (rr.gender IS NULL OR rr.gender = #{gender, jdbcType=INTEGER}) " +
            "AND (#{age, jdbcType=INTEGER} IS NULL OR rr.age_min IS NULL OR rr.age_min &lt;= #{age, jdbcType=INTEGER}) " +
            "AND (#{age, jdbcType=INTEGER} IS NULL OR rr.age_max IS NULL OR rr.age_max &gt;= #{age, jdbcType=INTEGER}) " +
            "WHERE t.parent_id IN " +
            "<foreach item='pid' collection='panelIds' open='(' separator=',' close=')'>#{pid}</foreach> " +
            "AND t.is_panel = 0 AND t.status = 1 " +
            "ORDER BY t.id, " +
            "CASE WHEN rr.gender = #{gender, jdbcType=INTEGER} THEN 0 WHEN rr.gender IS NULL THEN 1 ELSE 2 END, " +
            "COALESCE(rr.age_max - rr.age_min, 999999) ASC, " +
            "CASE WHEN rr.ref_low IS NOT NULL OR rr.ref_high IS NOT NULL OR rr.ref_text IS NOT NULL THEN 0 ELSE 1 END, " +
            "rr.id ASC" +
            "</script>")
    List<Map<String, Object>> selectTestIndicators(@Param("panelIds") List<Long> panelIds,
                                                    @Param("gender") Integer gender,
                                                    @Param("age") Integer age);

    /**
     * 查询已存在的检验结果 (通讯程序回传的)
     */
    @Select("SELECT r.id, r.sample_id, r.test_item_id, r.result_value, r.result_text, " +
            "r.unit, r.ref_range, r.abnormal_flag, r.is_critical, " +
            "r.instrument_id, r.test_time, r.audit_status, " +
            "r.last_result_value, r.last_result_time, r.last_abnormal_flag, " +
            "r.od_value, r.cutoff_value, r.sc_ratio, " +
            "r.auto_audit, r.auto_audit_message, r.item_knowledge, r.image_url, " +
            "t.item_code, t.item_name, t.item_name_en, t.category, " +
            "t.parent_id, p.item_name AS panel_name " +
            "FROM lis_result r " +
            "LEFT JOIN lis_test_item t ON r.test_item_id = t.id " +
            "LEFT JOIN lis_test_item p ON t.parent_id = p.id " +
            "WHERE r.sample_id = #{sampleId} " +
            "ORDER BY t.parent_id, t.id")
    List<Map<String, Object>> selectExistingResults(@Param("sampleId") Long sampleId);

    /**
     * 汇总某标本下「检验项目」名称（与详情页「共 N 个检验项目」panelList 口径一致）。
     * 优先从已回传结果 JOIN 项目字典 -> 取 DISTINCT panel_name（无 panel 时回退 item_name）；
     * 若标本尚无任何结果，再回退申请项目 oi.test_item_name，保证卡片显示非空。
     * 返回 '、' 分隔的字符串（空时返回 ""）。
     */
    @Select("SELECT COALESCE( " +
            "  (SELECT string_agg(DISTINCT COALESCE(p.item_name, t.item_name, r.result_text, r.test_item_id::text), '、' " +
            "                  ORDER BY COALESCE(p.item_name, t.item_name, r.result_text, r.test_item_id::text)) " +
            "   FROM lis_result r " +
            "   LEFT JOIN lis_test_item t ON t.id = r.test_item_id " +
            "   LEFT JOIN lis_test_item p ON p.id = t.parent_id " +
            "   WHERE r.sample_id = #{sampleId} " +
            "   HAVING COUNT(r.id) > 0), " +
            "  (SELECT string_agg(DISTINCT oi.test_item_name, '、' ORDER BY oi.test_item_name) " +
            "   FROM lis_order_item oi WHERE oi.order_id = #{orderId}), " +
            "  '')")
    String selectTestItemNamesForCard(@Param("sampleId") Long sampleId,
                                      @Param("orderId") Long orderId);

    /**
     * 查询指定患者/指标的"上一次"已完成结果 (用于技师工作站"上次结果"列展示)
     * 规则: 标本已报告 (lis_sample.status >= 6), 时间早于当前 sample 时间
     */
    @Select("SELECT r.result_value, r.test_time, r.abnormal_flag " +
            "FROM lis_result r " +
            "JOIN lis_sample s ON r.sample_id = s.id " +
            "JOIN lis_lab_order o ON s.order_id = o.id " +
            "WHERE o.patient_id = #{patientId} " +
            "  AND r.test_item_id = #{testItemId} " +
            "  AND s.status >= 6 " +
            "  AND (s.report_time IS NULL OR s.report_time < #{currentTime}) " +
            "ORDER BY COALESCE(s.report_time, s.test_start_time, s.receive_time) DESC NULLS LAST " +
            "LIMIT 1")
    Map<String, Object> selectLastResult(
            @Param("patientId") Long patientId,
            @Param("testItemId") Long testItemId,
            @Param("currentTime") java.time.LocalDateTime currentTime);

    /* ============ 批量操作 SQL 方法 ============ */

    /** 更新标本状态 (用于批量审核/撤销审核) */
    @Update("UPDATE lis_sample SET status = #{status}, report_time = CASE WHEN #{status} >= 6 THEN #{now} ELSE report_time END, " +
            "update_time = #{now} WHERE id = #{sampleId} AND deleted = 0")
    int updateSampleStatus(@Param("sampleId") Long sampleId,
                           @Param("status") int status,
                           @Param("now") java.time.LocalDateTime now);

    /** 撤销审核/完成: 允许将 status <= 6(含已报告/已完成) 的标本退回核收状态, 更高状态不允许 */
    @Update("UPDATE lis_sample SET status = #{status}, update_time = #{now} " +
            "WHERE id = #{sampleId} AND deleted = 0 AND status <= 6")
    int unauditSample(@Param("sampleId") Long sampleId,
                      @Param("status") int status,
                      @Param("now") java.time.LocalDateTime now);

    /** 更新检验人 */
    @Update("UPDATE lis_sample SET inspector = #{inspector}, " +
            "inspector_user_id = #{inspectorUserId}, update_time = #{now} " +
            "WHERE id = #{sampleId} AND deleted = 0")
    int updateInspector(@Param("sampleId") Long sampleId,
                        @Param("inspector") String inspector,
                        @Param("inspectorUserId") Long inspectorUserId,
                        @Param("now") java.time.LocalDateTime now);

    /** 按标本+指标id upsert 结果值 */
    @Insert("INSERT INTO lis_result (sample_id, test_item_id, result_value, update_time, create_time) " +
            "VALUES (#{sampleId}, #{testItemId}, #{resultValue}, #{now}, #{now}) " +
            "ON CONFLICT (sample_id, test_item_id) DO UPDATE SET " +
            "result_value = EXCLUDED.result_value, update_time = EXCLUDED.update_time")
    int upsertResultById(@Param("sampleId") Long sampleId,
                         @Param("testItemId") Long testItemId,
                         @Param("resultValue") String resultValue,
                         @Param("now") java.time.LocalDateTime now);

    /** 按标本+指标code upsert 结果值 */
    @Insert("INSERT INTO lis_result (sample_id, test_item_id, result_value, update_time, create_time) " +
            "SELECT #{sampleId}, t.id, #{resultValue}, #{now}, #{now} " +
            "FROM lis_test_item t WHERE t.item_code = #{itemCode} AND t.is_panel = 0 AND t.status = 1 " +
            "LIMIT 1 " +
            "ON CONFLICT (sample_id, test_item_id) DO UPDATE SET " +
            "result_value = EXCLUDED.result_value, update_time = EXCLUDED.update_time")
    int upsertResultByCode(@Param("sampleId") Long sampleId,
                           @Param("itemCode") String itemCode,
                           @Param("resultValue") String resultValue,
                           @Param("now") java.time.LocalDateTime now);

    /** 查询待修改的指标（根据 scope/panels/indicators） */
    @Select("<script>" +
            "SELECT t.id AS test_item_id, t.item_code, t.item_name, t.parent_id, r.result_value " +
            "FROM lis_test_item t " +
            "LEFT JOIN lis_result r ON r.test_item_id = t.id AND r.sample_id = #{sampleId} " +
            "WHERE t.is_panel = 0 AND t.status = 1 " +
            " AND t.parent_id IN (" +
            "   SELECT DISTINCT oi.test_item_id FROM lis_sample_order_item soi " +
            "   JOIN lis_order_item oi ON soi.order_item_id = oi.id " +
            "   WHERE soi.sample_id = #{sampleId}" +
            " ) " +
            "<choose>" +
            "<when test=\"scope == 'abnormal'\">" +
            "  AND r.abnormal_flag IS NOT NULL AND r.abnormal_flag != '' " +
            "</when>" +
            "<when test=\"scope == 'panel' and panelIds != null and panelIds.size() > 0\">" +
            "  AND t.parent_id IN <foreach item='pid' collection='panelIds' open='(' separator=',' close=')'>#{pid}</foreach> " +
            "</when>" +
            "<when test=\"scope == 'indicators' and indicatorCodes != null and indicatorCodes.size() > 0\">" +
            "  AND t.item_code IN <foreach item='c' collection='indicatorCodes' open='(' separator=',' close=')'>#{c}</foreach> " +
            "</when>" +
            "</choose>" +
            "ORDER BY t.parent_id, t.id" +
            "</script>")
    List<Map<String, Object>> selectIndicatorsForModify(
            @Param("sampleId") Long sampleId,
            @Param("scope") String scope,
            @Param("panelIds") List<Long> panelIds,
            @Param("indicatorCodes") List<String> indicatorCodes);

    /** 让步核收 - 打标 */
    @Update("UPDATE lis_sample SET waive_flag = 1, waive_reason = #{reason}, update_time = #{now} " +
            "WHERE id = #{sampleId} AND deleted = 0")
    int markWaiveReceive(@Param("sampleId") Long sampleId,
                         @Param("reason") String reason,
                         @Param("now") java.time.LocalDateTime now);

    /** 标本复制 - 复制一行标本（简化：生成一条新标本记录，条码带"-CP"后缀） */
    @Insert("INSERT INTO lis_sample (" +
            "sample_barcode, sample_no, order_id, patient_id, patient_name, sample_type, " +
            "container_type, status, is_urgent, department, instrument_id, operator_id, " +
            "collect_time, receive_time, test_start_time, create_time, update_time, remark, deleted) " +
            "SELECT sample_barcode || '-CP' || to_char(#{now}, 'HH24MISS'), " +
            "sample_no || '-CP', order_id, patient_id, patient_name, sample_type, " +
            "container_type, 4, is_urgent, department, instrument_id, operator_id, " +
            "collect_time, #{now}, NULL, #{now}, #{now}, '复制自动生成', 0 " +
            "FROM lis_sample WHERE id = #{sampleId}")
    int copySample(@Param("sampleId") Long sampleId,
                   @Param("now") java.time.LocalDateTime now);

    /** 标本拆分 - 插入子标本 */
    @Insert("INSERT INTO lis_sample (" +
            "sample_barcode, sample_no, order_id, patient_id, patient_name, sample_type, " +
            "container_type, status, is_urgent, department, instrument_id, operator_id, " +
            "collect_time, receive_time, create_time, update_time, remark, parent_id, deleted) " +
            "SELECT sample_barcode || '-S' || #{childNo}, " +
            "sample_no || '-S' || #{childNo}, order_id, patient_id, patient_name, sample_type, " +
            "container_type, 4, is_urgent, department, instrument_id, operator_id, " +
            "collect_time, receive_time, #{now}, #{now}, " +
            "CASE WHEN #{remark} IS NOT NULL THEN '拆分:' || #{remark} ELSE '拆分标本' END, " +
            "#{sampleId}, 0 " +
            "FROM lis_sample WHERE id = #{sampleId}")
    int createSplitChild(@Param("sampleId") Long sampleId,
                         @Param("childNo") int childNo,
                         @Param("remark") String remark,
                         @Param("now") java.time.LocalDateTime now);

    /** 标本拆分 - 打标标本已拆分 */
    @Update("UPDATE lis_sample SET split_flag = 1, update_time = #{now} WHERE id = #{sampleId} AND deleted = 0")
    int markSplitted(@Param("sampleId") Long sampleId,
                     @Param("now") java.time.LocalDateTime now);

    /** 撤销拆分 - 打标 */
    @Update("UPDATE lis_sample SET split_flag = 0, update_time = #{now} WHERE id = #{sampleId} AND deleted = 0")
    int markUnsplitted(@Param("sampleId") Long sampleId,
                       @Param("now") java.time.LocalDateTime now);

    /** 撤销拆分 - 物理删除子标本 */
    @Delete("DELETE FROM lis_sample WHERE parent_id = #{sampleId} AND deleted = 0")
    int removeSplitChildren(@Param("sampleId") Long sampleId);

    /** 标本销毁 - 逻辑删除 + 记录原因 */
    @Update("UPDATE lis_sample SET deleted = 1, destroy_reason = #{reason}, destroy_operator = #{operator}, " +
            "destroy_time = #{now}, update_time = #{now} WHERE id = #{sampleId} AND deleted = 0")
    int markDestroyed(@Param("sampleId") Long sampleId,
                      @Param("reason") String reason,
                      @Param("operator") String operator,
                      @Param("now") java.time.LocalDateTime now);

    /* ============ 收藏夹 ============ */

    /** 查询所有收藏 */
    @Select("SELECT id, name, type, payload, create_time AS createTime " +
            "FROM lis_technician_favorite ORDER BY create_time DESC")
    List<Map<String, Object>> selectFavorites();

    /** 新增收藏 */
    @Insert("INSERT INTO lis_technician_favorite (name, type, payload, create_time) " +
            "VALUES (#{name}, #{type}, CAST(#{payloadJson} AS JSON), #{now})")
    int insertFavorite(@Param("name") String name,
                       @Param("type") String type,
                       @Param("payloadJson") String payloadJson,
                       @Param("now") java.time.LocalDateTime now);

    /** 删除收藏 */
    @Delete("DELETE FROM lis_technician_favorite WHERE id = #{id}")
    int deleteFavorite(@Param("id") Long id);

    /* ============ 撤销核收 / 修改标本号 ============ */

    /** 撤销核收: 状态退回已签收(3), 清除核收时间/仪器/标本号 */
    @Update("UPDATE lis_sample SET status = 3, sample_no = NULL, instrument_id = NULL, " +
            "receive_time = NULL, update_time = #{now} " +
            "WHERE id = #{sampleId} AND status = 4 AND deleted = 0")
    int cancelReceive(@Param("sampleId") Long sampleId,
                      @Param("now") java.time.LocalDateTime now);

    /** 修改标本号 */
    @Update("UPDATE lis_sample SET sample_no = #{sampleNo}, update_time = #{now} " +
            "WHERE id = #{sampleId} AND deleted = 0")
    int updateSampleNo(@Param("sampleId") Long sampleId,
                       @Param("sampleNo") String sampleNo,
                       @Param("now") java.time.LocalDateTime now);

    /**
     * 修改技师工作站可编辑的标本信息: 标本号/核收时间/核收人/检验时间。
     * 仅允许核收后未审核 (status = 4/5) 的标本修改, 各字段可独立传值。
     */
    @Update("<script>" +
            "UPDATE lis_sample SET update_time = #{now} " +
            "<if test='sampleNo != null'> , sample_no = #{sampleNo} </if>" +
            "<if test='receiveTime != null'> , receive_time = #{receiveTime} </if>" +
            "<if test='testStartTime != null'> , test_start_time = #{testStartTime} </if>" +
            "<if test='receiver != null'> , receiver = #{receiver} </if>" +
            "<if test='receiverId != null'> , receiver_id = #{receiverId} </if>" +
            "<if test='collectTime != null'> , collect_time = #{collectTime} </if>" +
            "WHERE id = #{sampleId} AND deleted = 0 AND status IN (4,5)" +
            "</script>")
    int updateSampleInfo(@Param("sampleId") Long sampleId,
                         @Param("sampleNo") String sampleNo,
                         @Param("receiveTime") java.time.LocalDateTime receiveTime,
                         @Param("testStartTime") java.time.LocalDateTime testStartTime,
                         @Param("collectTime") java.time.LocalDateTime collectTime,
                         @Param("receiver") String receiver,
                         @Param("receiverId") Long receiverId,
                         @Param("now") java.time.LocalDateTime now);

    /* ============ 条码补打记录 / 工作站签到 (表见 docs/database/add-technician-signin-print.sql) ============ */

    /**
     * 写入条码补打记录 (print_type 固定为 REPRINT)
     *
     * @return 影响行数, 正常为 1
     */
    @Insert("INSERT INTO lis_barcode_print_log " +
            "(sample_id, sample_barcode, print_type, copies, printer_name, reason, " +
            " operator_id, operator_name, print_time) " +
            "VALUES (#{sampleId}, #{sampleBarcode}, 'REPRINT', #{copies}, #{printerName}, #{reason}, " +
            " #{operatorId}, #{operatorName}, #{now})")
    int insertBarcodePrintLog(@Param("sampleId") Long sampleId,
                              @Param("sampleBarcode") String sampleBarcode,
                              @Param("copies") Integer copies,
                              @Param("printerName") String printerName,
                              @Param("reason") String reason,
                              @Param("operatorId") Long operatorId,
                              @Param("operatorName") String operatorName,
                              @Param("now") java.time.LocalDateTime now);

    /**
     * 新增一条在岗签到记录 (status 固定为 1 在岗)
     *
     * @param signinType   INSTRUMENT / AUTO_AUDIT
     * @param instrumentId 仪器ID, AUTO_AUDIT 时传 null
     * @return 影响行数, 正常为 1
     */
    @Insert("INSERT INTO lis_station_signin " +
            "(signin_type, instrument_id, operator_id, operator_name, action, " +
            " signin_time, signout_time, status, remark) " +
            "VALUES (#{signinType}, #{instrumentId}, #{operatorId}, #{operatorName}, #{action}, " +
            " #{now}, NULL, 1, #{remark})")
    int insertSignin(@Param("signinType") String signinType,
                     @Param("instrumentId") Long instrumentId,
                     @Param("operatorId") Long operatorId,
                     @Param("operatorName") String operatorName,
                     @Param("action") String action,
                     @Param("remark") String remark,
                     @Param("now") java.time.LocalDateTime now);

    /**
     * 关闭仍在岗的签到记录, 保证同一维度只有一条在岗记录。
     * INSTRUMENT 按 instrument_id 维度关闭, 其余类型 (AUTO_AUDIT) 按 operator_id 维度关闭。
     *
     * @param signinType INSTRUMENT / AUTO_AUDIT
     * @param keyId      INSTRUMENT 时为仪器ID; AUTO_AUDIT 时为操作人ID
     * @return 实际关闭的行数, 无在岗记录时为 0
     */
    @Update("<script>" +
            "UPDATE lis_station_signin SET status = 0, signout_time = #{now} " +
            "WHERE signin_type = #{signinType} AND status = 1 " +
            "<choose>" +
            "<when test=\"signinType == 'INSTRUMENT'\"> AND instrument_id = #{keyId} </when>" +
            "<otherwise> AND operator_id = #{keyId} </otherwise>" +
            "</choose>" +
            "</script>")
    int closeOpenSignin(@Param("signinType") String signinType,
                        @Param("keyId") Long keyId,
                        @Param("now") java.time.LocalDateTime now);
}
