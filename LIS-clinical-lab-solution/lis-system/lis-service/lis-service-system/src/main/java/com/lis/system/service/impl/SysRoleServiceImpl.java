package com.lis.system.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.lis.common.core.exception.BusinessException;
import com.lis.common.core.result.PageResult;
import com.lis.system.entity.SysRole;
import com.lis.system.entity.SysRolePermission;
import com.lis.system.entity.SysUserRole;
import com.lis.system.mapper.SysRoleMapper;
import com.lis.system.mapper.SysRolePermissionMapper;
import com.lis.system.mapper.SysUserRoleMapper;
import com.lis.system.service.SysRoleService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

import java.util.List;

/**
 * 角色服务实现 - 角色CRUD、分配权限
 */
@Service
public class SysRoleServiceImpl extends ServiceImpl<SysRoleMapper, SysRole> implements SysRoleService {

    private final SysRolePermissionMapper sysRolePermissionMapper;
    private final SysUserRoleMapper sysUserRoleMapper;

    @Autowired
    public SysRoleServiceImpl(SysRolePermissionMapper sysRolePermissionMapper,
                              SysUserRoleMapper sysUserRoleMapper) {
        this.sysRolePermissionMapper = sysRolePermissionMapper;
        this.sysUserRoleMapper = sysUserRoleMapper;
    }

    @Override
    public PageResult<SysRole> page(long current, long size, String roleName, Integer status) {
        LambdaQueryWrapper<SysRole> wrapper = new LambdaQueryWrapper<>();
        if (StringUtils.hasText(roleName)) {
            wrapper.like(SysRole::getRoleName, roleName);
        }
        if (status != null) {
            wrapper.eq(SysRole::getStatus, status);
        }
        wrapper.orderByDesc(SysRole::getCreateTime);

        IPage<SysRole> page = new Page<>(current, size);
        IPage<SysRole> result = this.page(page, wrapper);
        return new PageResult<>(result.getRecords(), result.getTotal(), result.getCurrent(), result.getSize());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public SysRole create(SysRole role) {
        if (role == null || !StringUtils.hasText(role.getRoleCode()) || !StringUtils.hasText(role.getRoleName())) {
            throw new BusinessException(400, "角色编码和名称不能为空");
        }
        Long count = this.lambdaQuery().eq(SysRole::getRoleCode, role.getRoleCode()).count();
        if (count != null && count > 0) {
            throw new BusinessException(400, "角色编码已存在");
        }
        if (role.getStatus() == null) {
            role.setStatus(1);
        }
        this.save(role);
        return role;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void update(SysRole role) {
        if (role == null || role.getId() == null) {
            throw new BusinessException(400, "角色ID不能为空");
        }
        this.updateById(role);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        if (id == null) {
            throw new BusinessException(400, "角色ID不能为空");
        }
        // 校验是否有用户绑定该角色
        Long userCount = sysUserRoleMapper.selectCount(
                new LambdaQueryWrapper<SysUserRole>().eq(SysUserRole::getRoleId, id));
        if (userCount != null && userCount > 0) {
            throw new BusinessException(400, "该角色已分配给用户, 不能删除");
        }
        this.removeById(id);
        // 删除角色权限关系
        sysRolePermissionMapper.delete(new LambdaQueryWrapper<SysRolePermission>()
                .eq(SysRolePermission::getRoleId, id));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void assignPermissions(Long roleId, List<Long> permissionIds) {
        if (roleId == null) {
            throw new BusinessException(400, "角色ID不能为空");
        }
        // 先删除旧权限关系
        sysRolePermissionMapper.delete(new LambdaQueryWrapper<SysRolePermission>()
                .eq(SysRolePermission::getRoleId, roleId));
        // 保存新权限关系
        if (CollectionUtils.isEmpty(permissionIds)) {
            return;
        }
        for (Long permId : permissionIds) {
            SysRolePermission rp = new SysRolePermission();
            rp.setRoleId(roleId);
            rp.setPermissionId(permId);
            sysRolePermissionMapper.insert(rp);
        }
    }

    @Override
    public List<SysRole> listEnabled() {
        return this.list(new LambdaQueryWrapper<SysRole>().eq(SysRole::getStatus, 1));
    }
}
