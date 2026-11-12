package com.lis.report.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.lis.report.entity.CriticalRecord;
import com.lis.report.vo.CriticalRecordVO;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 危急值处理记录 Mapper
 */
public interface CriticalRecordMapper extends BaseMapper<CriticalRecord> {

    String CRITICAL_SELECT = "SELECT DISTINCT ON (r.id) " +
            "r.id AS result_id, r.sample_id, r.test_item_id, " +
            "s.patient_name, t.item_name AS test_item_name, " +
            "r.result_value, r.unit, r.ref_range, r.is_critical, " +
            "rr.critical_low, rr.critical_high, " +
            "cr.id AS record_id, cr.notifier, cr.notify_time, cr.sender, cr.send_time, cr.remark, " +
            "COALESCE(cr.status, 0) AS status, r.create_time " +
            "FROM lis_result r " +
            "JOIN lis_sample s ON s.id = r.sample_id " +
            "LEFT JOIN lis_test_item t ON t.id = r.test_item_id " +
            "LEFT JOIN lis_reference_range rr ON rr.test_item_id = r.test_item_id " +
            "LEFT JOIN lis_critical_record cr ON cr.result_id = r.id ";

    /** 危急值列表 (含处理记录), 支持按结果创建时间范围过滤 */
    @Select("<script>" +
            "SELECT * FROM (" + CRITICAL_SELECT +
            "WHERE r.is_critical = 1 " +
            "<if test='startDate != null and startDate != \"\"'> AND r.create_time &gt;= #{startDate}::date </if>" +
            "<if test='endDate != null and endDate != \"\"'> AND r.create_time &lt;= (#{endDate}::date + interval '1 day') </if>" +
            "ORDER BY r.id, " +
            "COALESCE(rr.critical_low, rr.critical_high) IS NOT NULL DESC, rr.id DESC" +
            ") t ORDER BY t.create_time DESC" +
            "</script>")
    List<CriticalRecordVO> selectCriticalList(@Param("startDate") String startDate,
                                              @Param("endDate") String endDate);

    /** 查询单个危急值详情 (含处理记录), 用于通知时填充冗余字段 */
    @Select("SELECT * FROM (" + CRITICAL_SELECT +
            "WHERE r.is_critical = 1 AND r.id = #{resultId} " +
            "ORDER BY r.id, " +
            "COALESCE(rr.critical_low, rr.critical_high) IS NOT NULL DESC, rr.id DESC" +
            ") t LIMIT 1")
    CriticalRecordVO selectCriticalDetail(@Param("resultId") Long resultId);
}