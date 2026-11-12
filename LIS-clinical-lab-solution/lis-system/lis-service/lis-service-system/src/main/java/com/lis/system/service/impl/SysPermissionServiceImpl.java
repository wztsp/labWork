package com.lis.system.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.lis.common.core.exception.BusinessException;
import com.lis.system.entity.SysPermission;
import com.lis.system.entity.SysRolePermission;
import com.lis.system.mapper.SysPermissionMapper;
import com.lis.system.mapper.SysRolePermissionMapper;
import com.lis.system.service.SysPermissionService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 权限服务实现 - 权限树管理
 */
@Service
public class SysPermissionServiceImpl extends ServiceImpl<SysPermissionMapper, SysPermission>
        implements SysPermissionService {

    private final SysRolePermissionMapper sysRolePermissionMapper;

    @Autowired
    public SysPermissionServiceImpl(SysRolePermissionMapper sysRolePermissionMapper) {
        this.sysRolePermissionMapper = sysRolePermissionMapper;
    }

    @Override
    public List<PermissionNode> tree() {
        List<SysPermission> all = this.list(new LambdaQueryWrapper<SysPermission>()
                .orderByAsc(SysPermission::getSortOrder));
        // 转换为节点
        List<PermissionNode> nodes = all.stream().map(this::toNode).collect(Collectors.toList());
        // 按 parentId 分组
        Map<Long, List<PermissionNode>> group = nodes.stream()
                .collect(Collectors.groupingBy(n -> n.getParentId() == null ? 0L : n.getParentId()));
        // 递归构建子节点
        nodes.forEach(n -> n.setChildren(group.getOrDefault(n.getId(), new ArrayList<>())));
        // 返回根节点 (parentId 为 0 或 null)
        List<PermissionNode> roots = nodes.stream()
                .filter(n -> n.getParentId() == null || n.getParentId() == 0L)
                .sorted(Comparator.comparingInt(n -> n.getSortOrder() == null ? 0 : n.getSortOrder()))
                .collect(Collectors.toList());
        sortTree(roots);
        return roots;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public SysPermission create(SysPermission permission) {
        if (permission == null || !StringUtils.hasText(permission.getPermCode()) || !StringUtils.hasText(permission.getPermName())) {
            throw new BusinessException(400, "权限编码和名称不能为空");
        }
        Long count = this.lambdaQuery().eq(SysPermission::getPermCode, permission.getPermCode()).count();
        if (count != null && count > 0) {
            throw new BusinessException(400, "权限编码已存在");
        }
        if (permission.getParentId() == null) {
            permission.setParentId(0L);
        }
        if (permission.getStatus() == null) {
            permission.setStatus(1);
        }
        if (permission.getSortOrder() == null) {
            permission.setSortOrder(0);
        }
        this.save(permission);
        return permission;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void update(SysPermission permission) {
        if (permission == null || permission.getId() == null) {
            throw new BusinessException(400, "权限ID不能为空");
        }
        this.updateById(permission);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        if (id == null) {
            throw new BusinessException(400, "权限ID不能为空");
        }
        // 校验是否有子节点
        Long childCount = this.lambdaQuery().eq(SysPermission::getParentId, id).count();
        if (childCount != null && childCount > 0) {
            throw new BusinessException(400, "存在子权限, 不能删除");
        }
        this.removeById(id);
        // 删除角色权限关系
        sysRolePermissionMapper.delete(new LambdaQueryWrapper<SysRolePermission>()
                .eq(SysRolePermission::getPermissionId, id));
    }

    private PermissionNode toNode(SysPermission p) {
        PermissionNode node = new PermissionNode();
        node.setId(p.getId());
        node.setParentId(p.getParentId());
        node.setPermCode(p.getPermCode());
        node.setPermName(p.getPermName());
        node.setPermType(p.getPermType());
        node.setPath(p.getPath());
        node.setComponent(p.getComponent());
        node.setIcon(p.getIcon());
        node.setSortOrder(p.getSortOrder());
        node.setStatus(p.getStatus());
        return node;
    }

    private void sortTree(List<PermissionNode> nodes) {
        if (nodes == null || nodes.isEmpty()) {
            return;
        }
        nodes.sort(Comparator.comparingInt(n -> n.getSortOrder() == null ? 0 : n.getSortOrder()));
        nodes.forEach(n -> sortTree(n.getChildren()));
    }
}
