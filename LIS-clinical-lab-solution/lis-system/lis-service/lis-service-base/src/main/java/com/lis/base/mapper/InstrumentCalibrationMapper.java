package com.lis.base.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.lis.base.entity.InstrumentCalibration;
import org.apache.ibatis.annotations.Mapper;

/**
 * 仪器校准记录 Mapper
 */
@Mapper
public interface InstrumentCalibrationMapper extends BaseMapper<InstrumentCalibration> {
}
