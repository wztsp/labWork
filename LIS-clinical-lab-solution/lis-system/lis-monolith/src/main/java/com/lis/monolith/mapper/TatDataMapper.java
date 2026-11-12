package com.lis.monolith.mapper;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.lis.monolith.entity.Message;
import com.lis.monolith.vo.TatScanRow;
import com.lis.monolith.vo.InfectiousScanRow;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * TAT / 消息 业务数据查询 (跨表)
 */
@Mapper
public interface TatDataMapper {

    /**
     * 查询需要监控 TAT 的标本-项目行
     * 目标: 已签收/已核收/检测中 且 未报告 的标本, 命中已启用的 TAT 规则
     */
    @Select("""
        SELECT s.id            AS sampleId,
               s.sample_barcode AS sampleBarcode,
               s.patient_name   AS patientName,
               s.sample_type    AS specimenType,
               s.is_urgent      AS isUrgent,
               s.sign_off_time  AS signOffTime,
               s.operator_id    AS operatorId,
               s.department     AS department,
               soi.test_item_id AS testItemId,
               ti.item_name     AS testItemName,
               r.urgent_timeout AS urgentTimeout,
               r.normal_timeout AS normalTimeout
        FROM lis_sample s
        JOIN lis_sample_order_item soi ON soi.sample_id = s.id
        JOIN lis_tat_rule r
             ON r.test_item_id = soi.test_item_id
            AND (r.specimen_type = '' OR r.specimen_type = s.sample_type)
            AND r.enabled = 1
        LEFT JOIN lis_test_item ti ON ti.id = soi.test_item_id
        WHERE s.deleted = 0
          AND s.status IN (3, 4, 5)
          AND s.sign_off_time IS NOT NULL
          AND s.report_time IS NULL
    """)
    List<TatScanRow> selectTatScanRows();

    /**
     * 查询已审核且命中传染病配置的结果行
     */
    @Select("""
        SELECT res.id           AS resultId,
               res.sample_id    AS sampleId,
               res.test_item_id AS testItemId,
               res.result_value AS resultValue,
               res.result_text  AS resultText,
               res.abnormal_flag AS abnormalFlag,
               res.audit_status AS auditStatus,
               inf.test_item_name AS testItemName,
               inf.positive_text AS positiveText,
               s.patient_name   AS patientName,
               s.sample_barcode AS sampleBarcode,
               s.department     AS department
        FROM lis_result res
        JOIN lis_infectious_item inf
             ON inf.test_item_id = res.test_item_id AND inf.enabled = 1
        JOIN lis_sample s ON s.id = res.sample_id
        WHERE res.audit_status > 0
          AND res.create_time >= #{since}
    """)
    List<InfectiousScanRow> selectInfectiousRows(@Param("since") LocalDateTime since);

    /**
     * 查询危急值结果详情 (用于消息展示)
     */
    @Select("""
        SELECT res.id            AS resultId,
               res.sample_id     AS sampleId,
               res.test_item_id  AS testItemId,
               res.result_value  AS resultValue,
               res.unit          AS unit,
               ti.item_name      AS testItemName,
               s.patient_name    AS patientName,
               s.sample_barcode  AS sampleBarcode,
               s.department      AS department,
               s.operator_id     AS operatorId
        FROM lis_result res
        LEFT JOIN lis_test_item ti ON ti.id = res.test_item_id
        LEFT JOIN lis_sample s ON s.id = res.sample_id
        WHERE res.id = #{resultId}
    """)
    Map<String, Object> selectCriticalDetail(@Param("resultId") Long resultId);

    /**
     * 按用户/科室查询其可见消息
     */
    @Select("""
        SELECT m.*
        FROM lis_message m
        WHERE (m.user_id = #{userId}
               OR (m.user_id IS NULL AND (m.department_id IS NULL OR m.department_id = #{deptId})))
        ORDER BY m.create_time DESC
    """)
    IPage<Message> pageMessages(Page<?> page,
                                @Param("userId") Long userId,
                                @Param("deptId") Long deptId);

    /**
     * 按用户/科室计算未读消息数
     */
    @Select("""
        SELECT COUNT(*)
        FROM lis_message m
        WHERE (m.user_id = #{userId}
               OR (m.user_id IS NULL AND (m.department_id IS NULL OR m.department_id = #{deptId})))
          AND m.is_read = 0
    """)
    long countUnread(@Param("userId") Long userId, @Param("deptId") Long deptId);
}