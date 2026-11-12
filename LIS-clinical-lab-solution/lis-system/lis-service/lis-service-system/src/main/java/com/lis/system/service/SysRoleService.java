package com.lis.system.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.lis.common.core.result.PageResult;
import com.lis.system.entity.SysRole;

import java.util.List;

/**
 * 角色服务接口 - 角色CRUD、分配权限
 */
public interface SysRoleService extends IService<SysRole> {

    /**
     * 分页查询角色
     */
    PageResult<SysRole> page(long current, long size, String roleName, Integer status);

    /**
     * 新增角色
     */
    SysRole create(SysRole role);

    /**
     * 修改角色
     */
    void update(SysRole role);

    /**
     * 删除角色
     */
    void delete(Long id);

    /**
     * 给角色分配权限
     */
    void assignPermissions(Long roleId, List<Long> permissionIds);

    /**
     * 查询全部启用角色
     */
    List<SysRole> listEnabled();
}
