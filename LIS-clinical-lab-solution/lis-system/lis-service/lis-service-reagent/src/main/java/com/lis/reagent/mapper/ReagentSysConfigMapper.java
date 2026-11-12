package com.lis.reagent.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.lis.reagent.entity.SysConfig;
import org.apache.ibatis.annotations.Mapper;

/**
 * 系统参数配置 Mapper，对应表 lis_sys_config。
 * <p>
 * 与 system 模块的 SysConfigMapper 同名不同表，单体聚合部署时需使用独立接口名避免 Bean 冲突。
 */
@Mapper
public interface ReagentSysConfigMapper extends BaseMapper<SysConfig> {
}
