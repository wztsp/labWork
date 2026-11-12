package com.lis.reagent.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;
import java.util.Map;

/**
 * 试剂批次追溯 Mapper (只读查询 lis_result 表)
 */
@Mapper
public interface ReagentTraceMapper {

    /**
     * 根据试剂批号查询使用了该批次的所有检测结果
     */
    @Select("SELECT r.id, r.sample_id AS sampleId, r.test_item_id AS testItemId, " +
            "r.result_value AS resultValue, r.unit, r.ref_range AS refRange, " +
            "r.abnormal_flag AS abnormalFlag, r.is_critical AS isCritical, " +
            "r.instrument_id AS instrumentId, r.test_time AS testTime, " +
            "r.reagent_lot_id AS reagentLotId, r.reagent_lot_no AS reagentLotNo " +
            "FROM lis_result r " +
            "WHERE r.reagent_lot_no = #{lotNo} " +
            "ORDER BY r.test_time DESC")
    List<Map<String, Object>> traceByLotNo(@Param("lotNo") String lotNo);
}
