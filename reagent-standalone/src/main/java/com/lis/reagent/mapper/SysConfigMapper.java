package com.lis.reagent.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.lis.reagent.entity.SysConfig;
import org.apache.ibatis.annotations.Mapper;

/**
 * 系统参数配置 Mapper，对应表 lis_sys_config。
 */
@Mapper
public interface SysConfigMapper extends BaseMapper<SysConfig> {
}
