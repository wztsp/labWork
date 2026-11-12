package com.lis.system.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.lis.common.core.exception.BusinessException;
import com.lis.common.core.result.PageResult;
import com.lis.system.entity.SysDepartment;
import com.lis.system.entity.SysRole;
import com.lis.system.entity.SysUser;
import com.lis.system.entity.SysUserRole;
import com.lis.system.mapper.SysDepartmentMapper;
import com.lis.system.mapper.SysRoleMapper;
import com.lis.system.mapper.SysUserMapper;
import com.lis.system.mapper.SysUserRoleMapper;
import com.lis.system.service.SysUserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 用户服务实现 - 用户CRUD、分配角色、重置密码
 */
@Service
public class SysUserServiceImpl extends ServiceImpl<SysUserMapper, SysUser> implements SysUserService {

    private final SysUserRoleMapper sysUserRoleMapper;
    private final SysDepartmentMapper sysDepartmentMapper;
    private final SysRoleMapper sysRoleMapper;
    private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    @Autowired
    public SysUserServiceImpl(SysUserRoleMapper sysUserRoleMapper,
                              SysDepartmentMapper sysDepartmentMapper,
                              SysRoleMapper sysRoleMapper) {
        this.sysUserRoleMapper = sysUserRoleMapper;
        this.sysDepartmentMapper = sysDepartmentMapper;
        this.sysRoleMapper = sysRoleMapper;
    }

    @Override
    public PageResult<SysUser> page(long current, long size, String username, String realName, Integer status) {
        LambdaQueryWrapper<SysUser> wrapper = new LambdaQueryWrapper<>();
        if (StringUtils.hasText(username)) {
            wrapper.like(SysUser::getUsername, username);
        }
        if (StringUtils.hasText(realName)) {
            wrapper.like(SysUser::getRealName, realName);
        }
        if (status != null) {
            wrapper.eq(SysUser::getStatus, status);
        }
        wrapper.orderByDesc(SysUser::getCreateTime);

        IPage<SysUser> page = new Page<>(current, size);
        IPage<SysUser> result = this.page(page, wrapper);
        List<SysUser> records = result.getRecords();

        if (!CollectionUtils.isEmpty(records)) {
            // 1) 批量填充科室名称
            Set<Long> deptIds = records.stream()
                    .map(SysUser::getDepartmentId)
                    .filter(Objects::nonNull)
                    .collect(Collectors.toSet());
            Map<Long, String> deptNameMap = Collections.emptyMap();
            if (!deptIds.isEmpty()) {
                List<SysDepartment> depts = sysDepartmentMapper.selectBatchIds(deptIds);
                deptNameMap = depts.stream()
                        .collect(Collectors.toMap(SysDepartment::getId, SysDepartment::getDeptName, (a, b) -> a));
            }

            // 2) 批量填充用户-角色关联
            Set<Long> userIds = records.stream().map(SysUser::getId).collect(Collectors.toSet());
            // userId -> [roleId, ...]
            Map<Long, List<Long>> userRoleIdsMap = new HashMap<>();
            if (!userIds.isEmpty()) {
                List<SysUserRole> urList = sysUserRoleMapper.selectList(new LambdaQueryWrapper<SysUserRole>()
                        .in(SysUserRole::getUserId, userIds));
                for (SysUserRole ur : urList) {
                    userRoleIdsMap.computeIfAbsent(ur.getUserId(), k -> new ArrayList<>()).add(ur.getRoleId());
                }
            }
            // roleId -> roleName
            Set<Long> allRoleIds = userRoleIdsMap.values().stream()
                    .flatMap(Collection::stream)
                    .collect(Collectors.toSet());
            Map<Long, String> roleNameMap = Collections.emptyMap();
            if (!allRoleIds.isEmpty()) {
                List<SysRole> roles = sysRoleMapper.selectBatchIds(allRoleIds);
                roleNameMap = roles.stream()
                        .collect(Collectors.toMap(SysRole::getId, SysRole::getRoleName, (a, b) -> a));
            }

            // 写回到每行
            for (SysUser u : records) {
                u.setPassword(null);
                if (u.getDepartmentId() != null) {
                    u.setDepartmentName(deptNameMap.get(u.getDepartmentId()));
                }
                List<Long> rids = userRoleIdsMap.getOrDefault(u.getId(), Collections.emptyList());
                u.setRoleIds(rids);
                if (!rids.isEmpty()) {
                    u.setRoleNames(rids.stream()
                            .map(roleNameMap::get)
                            .filter(Objects::nonNull)
                            .collect(Collectors.toList()));
                } else {
                    u.setRoleNames(Collections.emptyList());
                }
            }
        }

        return new PageResult<>(records, result.getTotal(), result.getCurrent(), result.getSize());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public SysUser create(SysUser user, List<Long> roleIds) {
        if (user == null || !StringUtils.hasText(user.getUsername()) || !StringUtils.hasText(user.getPassword())) {
            throw new BusinessException(400, "用户名和密码不能为空");
        }
        // 校验用户名唯一
        Long count = this.lambdaQuery().eq(SysUser::getUsername, user.getUsername()).count();
        if (count != null && count > 0) {
            throw new BusinessException(400, "用户名已存在");
        }
        user.setPassword(passwordEncoder.encode(user.getPassword()));
        if (user.getStatus() == null) {
            user.setStatus(1);
        }
        this.save(user);

        // 保存用户角色关系
        saveUserRoles(user.getId(), roleIds);
        user.setPassword(null);
        return user;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void update(SysUser user) {
        if (user == null || user.getId() == null) {
            throw new BusinessException(400, "用户ID不能为空");
        }
        // 不允许通过此接口修改密码
        user.setPassword(null);
        this.updateById(user);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        if (id == null) {
            throw new BusinessException(400, "用户ID不能为空");
        }
        this.removeById(id);
        // 删除用户角色关系
        sysUserRoleMapper.delete(new LambdaQueryWrapper<SysUserRole>()
                .eq(SysUserRole::getUserId, id));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void assignRoles(Long userId, List<Long> roleIds) {
        if (userId == null) {
            throw new BusinessException(400, "用户ID不能为空");
        }
        // 先删除旧的角色关系
        sysUserRoleMapper.delete(new LambdaQueryWrapper<SysUserRole>()
                .eq(SysUserRole::getUserId, userId));
        // 再保存新的角色关系
        saveUserRoles(userId, roleIds);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void resetPassword(Long userId, String newPassword) {
        if (userId == null || !StringUtils.hasText(newPassword)) {
            throw new BusinessException(400, "用户ID和新密码不能为空");
        }
        SysUser update = new SysUser();
        update.setId(userId);
        update.setPassword(passwordEncoder.encode(newPassword));
        this.updateById(update);
    }

    private void saveUserRoles(Long userId, List<Long> roleIds) {
        if (CollectionUtils.isEmpty(roleIds)) {
            return;
        }
        for (Long roleId : roleIds) {
            SysUserRole ur = new SysUserRole();
            ur.setUserId(userId);
            ur.setRoleId(roleId);
            sysUserRoleMapper.insert(ur);
        }
    }
}
