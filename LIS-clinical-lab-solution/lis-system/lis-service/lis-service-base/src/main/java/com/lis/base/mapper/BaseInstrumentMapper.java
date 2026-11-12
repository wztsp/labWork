package com.lis.base.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.lis.base.entity.Instrument;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface BaseInstrumentMapper extends BaseMapper<Instrument> {
}
