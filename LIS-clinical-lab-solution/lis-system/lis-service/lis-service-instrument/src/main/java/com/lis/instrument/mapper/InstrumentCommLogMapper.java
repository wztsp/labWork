package com.lis.instrument.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.lis.instrument.entity.InstrumentCommLog;
import org.apache.ibatis.annotations.Mapper;

/**
 * 仪器通讯日志 Mapper
 */
@Mapper
public interface InstrumentCommLogMapper extends BaseMapper<InstrumentCommLog> {
}
