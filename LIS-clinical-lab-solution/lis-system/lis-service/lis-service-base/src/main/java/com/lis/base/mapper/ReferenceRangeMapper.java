package com.lis.base.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.lis.base.entity.ReferenceRange;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface ReferenceRangeMapper extends BaseMapper<ReferenceRange> {

    /**
     * 分页查询参考范围 (JOIN lis_test_item 获取指标名称和编码)
     * 注: PostgreSQL 无法推断 IS NULL 参数类型, 使用 CAST 显式指定
     */
    @Select("SELECT r.*, t.item_name AS test_item_name, t.item_code AS test_item_code " +
            "FROM lis_reference_range r " +
            "LEFT JOIN lis_test_item t ON r.test_item_id = t.id " +
            "WHERE (CAST(#{testItemId} AS BIGINT) IS NULL OR r.test_item_id = #{testItemId}) " +
            "AND (CAST(#{specimenType} AS VARCHAR) IS NULL OR r.specimen_type = #{specimenType}) " +
            "AND (CAST(#{instrumentId} AS BIGINT) IS NULL OR r.instrument_id = #{instrumentId}) " +
            "ORDER BY r.test_item_id DESC")
    IPage<ReferenceRange> selectPageWithItemName(IPage<ReferenceRange> page,
                                                  @Param("testItemId") Long testItemId,
                                                  @Param("specimenType") String specimenType,
                                                  @Param("instrumentId") Long instrumentId);
}
