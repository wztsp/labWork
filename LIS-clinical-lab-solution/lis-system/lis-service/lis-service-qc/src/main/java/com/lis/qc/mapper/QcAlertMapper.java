package com.lis.qc.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.lis.qc.entity.QcAlert;
import org.apache.ibatis.annotations.Mapper;

/**
 * 质控偏差趋势预警 Mapper
 */
@Mapper
public interface QcAlertMapper extends BaseMapper<QcAlert> {
}
