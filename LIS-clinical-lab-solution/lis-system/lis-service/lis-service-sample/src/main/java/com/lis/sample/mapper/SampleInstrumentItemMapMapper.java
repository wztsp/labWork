package com.lis.sample.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 仪器项目映射 Mapper (跨表查询, 用于核收时仪器能力校验)
 * 直接查询 lis_instrument_item_map 与 lis_instrument 表 (与标本同库)
 */
@Mapper
public interface SampleInstrumentItemMapMapper {

    /**
     * 查询指定仪器支持的全部检验项目ID
     */
    @Select("SELECT test_item_id FROM lis_instrument_item_map WHERE instrument_id = #{instrumentId}")
    List<Long> selectTestItemIdsByInstrumentId(@Param("instrumentId") Long instrumentId);

    /**
     * 查询仪器名称
     */
    @Select("SELECT instrument_name FROM lis_instrument WHERE id = #{instrumentId}")
    String selectInstrumentNameById(@Param("instrumentId") Long instrumentId);

    /**
     * 查询仪器是否存在且启用
     */
    @Select("SELECT COUNT(1) FROM lis_instrument WHERE id = #{instrumentId} AND status = 1")
    int countEnabledById(@Param("instrumentId") Long instrumentId);

    /**
     * 查询指定仪器已核收标本的最大标本号 (按数值降序取首条)
     * 返回字段: sample_no
     */
    @Select("SELECT sample_no FROM lis_sample " +
            "WHERE instrument_id = #{instrumentId} " +
            "AND sample_no IS NOT NULL " +
            "AND sample_no ~ '^[0-9]+$' " +
            "ORDER BY CAST(sample_no AS BIGINT) DESC LIMIT 1")
    String selectMaxSampleNoByInstrumentId(@Param("instrumentId") Long instrumentId);
}
