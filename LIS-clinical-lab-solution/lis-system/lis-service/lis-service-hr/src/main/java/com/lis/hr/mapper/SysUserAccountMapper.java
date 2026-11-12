package com.lis.hr.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.lis.hr.entity.SysUserAccount;

/**
 * 系统账号投影 Mapper (B-4 离职回收)。
 * <p>
 * 仅承载 sys_user 停用相关的读/写, 由 {@code LisHrApplication} 的
 * {@code @MapperScan("com.lis.hr.mapper")} 自动扫描注册。
 */
public interface SysUserAccountMapper extends BaseMapper<SysUserAccount> {
}
