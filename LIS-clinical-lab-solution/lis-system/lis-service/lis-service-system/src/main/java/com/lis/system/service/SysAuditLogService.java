package com.lis.system.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.lis.common.core.result.PageResult;
import com.lis.system.entity.SysAuditLog;

/**
 * 操作日志服务接口 - 操作日志记录与查询
 */
public interface SysAuditLogService extends IService<SysAuditLog> {

    /**
     * 记录操作日志
     */
    void record(SysAuditLog log);

    /**
     * 分页查询操作日志
     */
    PageResult<SysAuditLog> page(long current, long size, String username, String module);
}
