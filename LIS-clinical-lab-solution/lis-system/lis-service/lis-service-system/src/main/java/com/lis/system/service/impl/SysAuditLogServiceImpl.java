package com.lis.system.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.lis.common.core.result.PageResult;
import com.lis.system.entity.SysAuditLog;
import com.lis.system.mapper.SysAuditLogMapper;
import com.lis.system.service.SysAuditLogService;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

/**
 * 操作日志服务实现 - 操作日志记录与查询
 */
@Service
public class SysAuditLogServiceImpl extends ServiceImpl<SysAuditLogMapper, SysAuditLog>
        implements SysAuditLogService {

    @Override
    public void record(SysAuditLog log) {
        if (log == null) {
            return;
        }
        this.save(log);
    }

    @Override
    public PageResult<SysAuditLog> page(long current, long size, String username, String module) {
        LambdaQueryWrapper<SysAuditLog> wrapper = new LambdaQueryWrapper<>();
        if (StringUtils.hasText(username)) {
            wrapper.like(SysAuditLog::getUsername, username);
        }
        if (StringUtils.hasText(module)) {
            wrapper.eq(SysAuditLog::getModule, module);
        }
        wrapper.orderByDesc(SysAuditLog::getCreateTime);

        IPage<SysAuditLog> page = new Page<>(current, size);
        IPage<SysAuditLog> result = this.page(page, wrapper);
        return new PageResult<>(result.getRecords(), result.getTotal(), result.getCurrent(), result.getSize());
    }
}
