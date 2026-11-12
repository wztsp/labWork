package com.lis.instrument.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.lis.instrument.entity.Instrument;
import org.apache.ibatis.annotations.Mapper;

/**
 * 仪器字典 Mapper (查询 lis_instrument 表)
 */
@Mapper
public interface InstrumentCommMapper extends BaseMapper<Instrument> {
}
