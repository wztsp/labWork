package com.lis.report.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.lis.report.entity.Report;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 报告 Mapper
 */
@Mapper
public interface ReportMapper extends BaseMapper<Report> {

    /**
     * 按就诊号查询已发布报告 (关联: lis_report -> lis_sample -> lis_visit)
     */
    @Select("SELECT r.* FROM lis_report r "
            + "JOIN lis_sample s ON r.sample_id = s.id "
            + "JOIN lis_visit v ON s.visit_id = v.id "
            + "WHERE v.visit_no = #{visitNo} AND r.status = 2 AND s.deleted = 0 "
            + "ORDER BY r.create_time DESC")
    List<Report> selectPublishedByVisitNo(@Param("visitNo") String visitNo);

    /**
     * 按身份证号哈希查询已发布报告 (关联: lis_report -> lis_patient)
     */
    @Select("SELECT r.* FROM lis_report r "
            + "JOIN lis_patient p ON r.patient_id = p.id "
            + "WHERE p.id_card_hash = #{idCardHash} AND r.status = 2 "
            + "ORDER BY r.create_time DESC")
    List<Report> selectPublishedByIdCardHash(@Param("idCardHash") String idCardHash);

    /**
     * 按就诊号 + 身份证号哈希 双条件查询已发布报告 (双重校验, 防止单字段猜测/遍历)
     */
    @Select("SELECT r.* FROM lis_report r "
            + "JOIN lis_sample s ON r.sample_id = s.id "
            + "JOIN lis_visit v ON s.visit_id = v.id "
            + "JOIN lis_patient p ON r.patient_id = p.id "
            + "WHERE v.visit_no = #{visitNo} AND p.id_card_hash = #{idCardHash} "
            + "AND r.status = 2 AND s.deleted = 0 "
            + "ORDER BY r.create_time DESC")
    List<Report> selectPublishedByVisitNoAndIdCardHash(@Param("visitNo") String visitNo,
                                                       @Param("idCardHash") String idCardHash);
}
