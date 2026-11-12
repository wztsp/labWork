package com.lis.qc.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;
import java.util.Map;

/**
 * 室内质控(IQC)查询: 质控项目列表(关联检验指标与仪器, 并取最近一次质控值/判定)。
 * 依据 iqc-module-design.md 的项目列表字段。
 */
@Mapper
public interface QcIqcMapper {

    @Select("SELECT l.id AS lot_id, l.lot_no, l.test_item_id AS item_id, l.instrument_id AS instrument_id, "
            + "l.target_value, l.sd, l.cv, l.level, l.status AS lot_status, "
            + "t.item_code, t.item_name, t.category AS group_name, t.method, t.unit, "
            + "i.instrument_name AS instrument_name, i.brand, i.model, "
            + "lr.measure_value AS last_value, lr.z_score AS last_z, lr.qc_result AS last_result "
            + "FROM lis_qc_lot l "
            + "LEFT JOIN lis_test_item t ON l.test_item_id = t.id "
            + "LEFT JOIN lis_instrument i ON l.instrument_id = i.id "
            + "LEFT JOIN LATERAL ( "
            + "  SELECT rr.measure_value, rr.z_score, rr.qc_result "
            + "  FROM lis_qc_record rr "
            + "  WHERE rr.qc_lot_id = l.id AND rr.deleted = 0 "
            + "  ORDER BY rr.measure_time DESC, rr.id DESC LIMIT 1 "
            + ") lr ON true "
            + "WHERE l.deleted = 0 "
            + "ORDER BY t.id, l.level, l.id")
    List<Map<String, Object>> selectProjectList(@Param("instrumentId") Long instrumentId);
}
