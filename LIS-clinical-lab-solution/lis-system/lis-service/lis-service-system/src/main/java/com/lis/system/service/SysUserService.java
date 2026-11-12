package com.lis.system.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.lis.common.core.result.PageResult;
import com.lis.system.entity.SysUser;

import java.util.List;

/**
 * 用户服务接口 - 用户CRUD、分配角色、重置密码
 */
public interface SysUserService extends IService<SysUser> {

    /**
     * 分页查询用户
     */
    PageResult<SysUser> page(long current, long size, String username, String realName, Integer status);

    /**
     * 新增用户
     */
    SysUser create(SysUser user, List<Long> roleIds);

    /**
     * 修改用户
     */
    void update(SysUser user);

    /**
     * 删除用户
     */
    void delete(Long id);

    /**
     * 给用户分配角色
     */
    void assignRoles(Long userId, List<Long> roleIds);

    /**
     * 重置密码
     */
    void resetPassword(Long userId, String newPassword);
}
