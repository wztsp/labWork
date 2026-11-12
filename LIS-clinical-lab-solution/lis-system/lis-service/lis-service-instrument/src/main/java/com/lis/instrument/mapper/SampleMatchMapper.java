package com.lis.instrument.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.lis.instrument.entity.Sample;
import org.apache.ibatis.annotations.Mapper;

/**
 * 标本 Mapper (仅用于仪器结果匹配, 查询 lis_sample 表)
 */
@Mapper
public interface SampleMatchMapper extends BaseMapper<Sample> {
}
