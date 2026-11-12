package com.lis.system.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.lis.system.entity.SysRolePermission;
import org.apache.ibatis.annotations.Mapper;

/**
 * 角色权限关联 Mapper
 */
@Mapper
public interface SysRolePermissionMapper extends BaseMapper<SysRolePermission> {
}
