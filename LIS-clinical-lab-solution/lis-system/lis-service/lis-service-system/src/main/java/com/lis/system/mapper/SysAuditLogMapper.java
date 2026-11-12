package com.lis.system.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.lis.system.entity.SysAuditLog;
import org.apache.ibatis.annotations.Mapper;

/**
 * 操作日志 Mapper
 */
@Mapper
public interface SysAuditLogMapper extends BaseMapper<SysAuditLog> {
}
