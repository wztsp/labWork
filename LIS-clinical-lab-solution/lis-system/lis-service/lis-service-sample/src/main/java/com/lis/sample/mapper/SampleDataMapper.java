package com.lis.sample.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.lis.sample.entity.Sample;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface SampleDataMapper extends BaseMapper<Sample> {
}
