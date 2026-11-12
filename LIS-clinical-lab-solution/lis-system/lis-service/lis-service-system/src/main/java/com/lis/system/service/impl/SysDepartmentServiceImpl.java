package com.lis.system.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.lis.common.core.exception.BusinessException;
import com.lis.system.entity.SysDepartment;
import com.lis.system.mapper.SysDepartmentMapper;
import com.lis.system.service.SysDepartmentService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 科室服务实现 - CRUD + 树形查询
 */
@Service
public class SysDepartmentServiceImpl extends ServiceImpl<SysDepartmentMapper, SysDepartment>
        implements SysDepartmentService {

    @Override
    public List<SysDepartment> listTree() {
        List<SysDepartment> all = this.list(new LambdaQueryWrapper<SysDepartment>()
                .orderByAsc(SysDepartment::getSortOrder)
                .orderByAsc(SysDepartment::getId));
        if (all == null || all.isEmpty()) return java.util.Collections.emptyList();

        // 1. 清空所有MyBatis返回对象的children，避免一级缓存中残留旧引用造成环
        all.forEach(d -> d.setChildren(null));

        // 2. 构造全新副本（脱离MyBatis缓存对象）构建树形，避免任何缓存副作用
        java.util.Map<Long, SysDepartment> copyMap = new java.util.LinkedHashMap<>();
        for (SysDepartment src : all) {
            SysDepartment copy = new SysDepartment();
            copy.setId(src.getId());
            copy.setDeptCode(src.getDeptCode());
            copy.setDeptName(src.getDeptName());
            copy.setParentId(src.getParentId());
            copy.setSortOrder(src.getSortOrder());
            copy.setStatus(src.getStatus());
            copy.setCreateTime(src.getCreateTime());
            copy.setChildren(null);
            copyMap.put(copy.getId(), copy);
        }

        // 3. 组装父子关系 (使用副本，保证每个节点仅挂载一次，同时避免根候选(parentId==0)被作为子节点挂载)
        java.util.Set<Long> mounted = new java.util.HashSet<>();
        for (SysDepartment d : copyMap.values()) {
            Long dId = d.getId();
            List<SysDepartment> children = new java.util.ArrayList<>();
            for (SysDepartment c : copyMap.values()) {
                Long cPid = c.getParentId() == null ? 0L : c.getParentId();
                if (cPid.equals(dId)            // c是d的孩子
                        && !cPid.equals(0L)     // parentId==0的节点只能作为根，不能被当作孩子
                        && !c.getId().equals(dId)    // 过滤自引用
                        && !mounted.contains(c.getId())) { // 防止重复挂载/环
                    children.add(c);
                    mounted.add(c.getId());
                }
            }
            if (!children.isEmpty()) {
                d.setChildren(children);
            }
        }

        // 4. 根节点: parentId==0/null 且 未被其它节点挂载 且 不是parentId==id的假根
        return copyMap.values().stream()
                .filter(d -> {
                    Long pid = d.getParentId() == null ? 0L : d.getParentId();
                    return pid == 0L && !d.getId().equals(pid) && !mounted.contains(d.getId());
                })
                .collect(Collectors.toList());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public SysDepartment create(SysDepartment dept) {
        if (dept == null || !StringUtils.hasText(dept.getDeptCode()) || !StringUtils.hasText(dept.getDeptName())) {
            throw new BusinessException(400, "科室编码和名称不能为空");
        }
        Long count = this.lambdaQuery().eq(SysDepartment::getDeptCode, dept.getDeptCode()).count();
        if (count != null && count > 0) {
            throw new BusinessException(400, "科室编码已存在");
        }
        // 新增时强制清空 id, 交由数据库自增主键生成, 防止前端误传 id=0 触发主键冲突
        dept.setId(null);
        if (dept.getParentId() == null) {
            dept.setParentId(0L);
        }
        if (dept.getSortOrder() == null) {
            dept.setSortOrder(0);
        }
        if (dept.getStatus() == null) {
            dept.setStatus(1);
        }
        dept.setCreateTime(LocalDateTime.now());
        this.save(dept);
        return dept;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void update(SysDepartment dept) {
        if (dept == null || dept.getId() == null) {
            throw new BusinessException(400, "科室ID不能为空");
        }
        this.updateById(dept);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        if (id == null) {
            throw new BusinessException(400, "科室ID不能为空");
        }
        // 递归删除所有子节点
        List<Long> toDelete = new ArrayList<>();
        toDelete.add(id);
        collectChildren(id, toDelete);
        this.removeByIds(toDelete);
    }

    private void collectChildren(Long parentId, List<Long> acc) {
        // parentId==0 是"顶级科室"哨兵, 不存在父节点为 0 的真实子节点,
        // 必须中断, 否则删除 id=0 时会误把所有顶级科室当作其子节点一并删除
        if (parentId == null || parentId == 0L) {
            return;
        }
        List<SysDepartment> children = this.list(new LambdaQueryWrapper<SysDepartment>()
                .eq(SysDepartment::getParentId, parentId));
        if (children == null || children.isEmpty()) return;
        for (SysDepartment c : children) {
            acc.add(c.getId());
            collectChildren(c.getId(), acc);
        }
    }
}
