package com.lis.report.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.lis.report.entity.Result;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;
import java.util.Map;

/**
 * 检验结果 Mapper
 */
@Mapper
public interface ResultMapper extends BaseMapper<Result> {

    /**
     * 查询指定患者某检验项目最近 20 次结果 (按检测时间升序返回, 关联 lis_sample 取 patient_id)
     */
    @Select("SELECT r.* FROM lis_result r "
            + "JOIN lis_sample s ON r.sample_id = s.id "
            + "WHERE s.patient_id = #{patientId} "
            + "AND r.test_item_id = #{testItemId} "
            + "AND s.deleted = 0 "
            + "ORDER BY r.test_time DESC "
            + "LIMIT 20")
    List<Result> selectRecentByPatientAndItem(@Param("patientId") Long patientId,
                                              @Param("testItemId") Long testItemId);

    /**
     * 查询指定患者某检验项目最近一条已发布报告中的历史结果 (用于 Delta 检查)
     * 关联 lis_report 取报告状态, 仅取 status=2(已发布) 的报告结果, 排除当前标本
     */
    @Select("SELECT r.* FROM lis_result r "
            + "JOIN lis_sample s ON r.sample_id = s.id "
            + "JOIN lis_report rep ON rep.sample_id = s.id "
            + "WHERE s.patient_id = #{patientId} "
            + "AND r.test_item_id = #{testItemId} "
            + "AND rep.status = 2 "
            + "AND r.sample_id <> #{currentSampleId} "
            + "AND s.deleted = 0 "
            + "ORDER BY r.test_time DESC "
            + "LIMIT 1")
    Result selectLastPublishedByPatientAndItem(@Param("patientId") Long patientId,
                                                @Param("testItemId") Long testItemId,
                                                @Param("currentSampleId") Long currentSampleId);

    /**
     * 查询指定检验项目的参考范围中的危急值上下限 (取首条配置了危急值范围的记录)
     * 返回字段: critical_low, critical_high
     */
    @Select("SELECT critical_low AS criticalLow, critical_high AS criticalHigh "
            + "FROM lis_reference_range "
            + "WHERE test_item_id = #{testItemId} "
            + "AND (critical_low IS NOT NULL OR critical_high IS NOT NULL) "
            + "LIMIT 1")
    Map<String, Object> selectCriticalRange(@Param("testItemId") Long testItemId);

    /**
     * 查询给定检验指标ID列表对应的父级检验项目(检验项目)信息
     * 返回字段: test_item_id, parent_id, parent_name, item_code, item_name
     */
    @Select({"<script>",
            "SELECT t.id AS test_item_id, t.parent_id AS parent_id, ",
            "p.item_name AS parent_name, t.item_code AS item_code, t.item_name AS item_name ",
            "FROM lis_test_item t ",
            "LEFT JOIN lis_test_item p ON t.parent_id = p.id ",
            "WHERE t.id IN ",
            "<foreach item='id' collection='testItemIds' open='(' separator=',' close=')'>#{id}</foreach>",
            "</script>"})
    List<Map<String, Object>> selectPanelInfoForItems(@Param("testItemIds") List<Long> testItemIds);

    /**
     * 查询标本所属申请单预期的检验项目 ID 列表 (关联 lis_sample.order_id -> lis_order_item)
     */
    @Select("SELECT DISTINCT oi.test_item_id FROM lis_order_item oi "
            + "JOIN lis_sample s ON s.order_id = oi.order_id "
            + "WHERE s.id = #{sampleId}")
    List<Long> selectExpectedItemIdsBySample(@Param("sampleId") Long sampleId);

    /**
     * 查询标本对应的患者 ID
     */
    @Select("SELECT patient_id FROM lis_sample WHERE id = #{sampleId} AND deleted = 0")
    Long selectPatientIdBySample(@Param("sampleId") Long sampleId);

    /**
     * 根据检验指标代码批量查询 lis_test_item 的 ID 与名称
     * 返回字段: id, item_code, item_name
     */
    @Select({"<script>",
            "SELECT id AS id, item_code AS itemCode, item_name AS itemName ",
            "FROM lis_test_item ",
            "WHERE item_code IN ",
            "<foreach item='code' collection='codes' open='(' separator=',' close=')'>#{code}</foreach>",
            "</script>"})
    List<Map<String, Object>> selectTestItemsByCodes(@Param("codes") List<String> codes);
}
