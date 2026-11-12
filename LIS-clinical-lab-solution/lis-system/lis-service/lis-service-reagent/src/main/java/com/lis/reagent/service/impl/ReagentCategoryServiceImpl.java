package com.lis.reagent.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.lis.common.core.exception.BusinessException;
import com.lis.reagent.dto.CategoryCreateDTO;
import com.lis.reagent.entity.Reagent;
import com.lis.reagent.entity.ReagentCategory;
import com.lis.reagent.mapper.ReagentCategoryMapper;
import com.lis.reagent.mapper.ReagentMapper;
import com.lis.reagent.service.ReagentCategoryService;
import com.lis.reagent.support.ReagentWarehouseGuard;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 试剂分类字典服务实现（R-06）。
 *
 * @author LIS Team
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ReagentCategoryServiceImpl extends ServiceImpl<ReagentCategoryMapper, ReagentCategory>
        implements ReagentCategoryService {

    private final ReagentCategoryMapper categoryMapper;
    private final ReagentMapper reagentMapper;

    @Override
    public List<ReagentCategory> tree() {
        List<ReagentCategory> all = this.list(new LambdaQueryWrapper<ReagentCategory>()
                .orderByAsc(ReagentCategory::getSortOrder)
                .orderByAsc(ReagentCategory::getId));
        // 按 parentId 组装（物化路径已由 Service 维护，此处用内存树避免重复查库）
        Map<Long, ReagentCategory> byId = new HashMap<>();
        for (ReagentCategory c : all) {
            c.setChildren(new ArrayList<>());
            byId.put(c.getId(), c);
        }
        List<ReagentCategory> roots = new ArrayList<>();
        for (ReagentCategory c : all) {
            ReagentCategory parent = c.getParentId() == null ? null : byId.get(c.getParentId());
            if (parent != null && !parent.getId().equals(c.getId())) {
                parent.getChildren().add(c);
            } else {
                roots.add(c);
            }
        }
        return roots;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ReagentCategory create(CategoryCreateDTO dto) {
        if (dto == null || dto.getCategoryCode() == null || dto.getCategoryCode().trim().isEmpty()) {
            throw new BusinessException("分类编码不能为空");
        }
        if (dto.getCategoryName() == null || dto.getCategoryName().trim().isEmpty()) {
            throw new BusinessException("分类名称不能为空");
        }
        Long existed = this.count(new LambdaQueryWrapper<ReagentCategory>()
                .eq(ReagentCategory::getCategoryCode, dto.getCategoryCode().trim()));
        if (existed > 0) {
            throw new BusinessException("分类编码已存在: " + dto.getCategoryCode().trim());
        }
        Long parentId = dto.getParentId() == null ? 0L : dto.getParentId();
        ReagentCategory parent = null;
        if (parentId != 0L) {
            parent = this.getById(parentId);
            if (parent == null) {
                throw new BusinessException("父分类不存在: " + parentId);
            }
        }
        ReagentCategory category = new ReagentCategory();
        category.setCategoryCode(dto.getCategoryCode().trim());
        category.setCategoryName(dto.getCategoryName().trim());
        category.setParentId(parentId);
        category.setHazardCategory(parseHazard(dto.getHazardCategory()));
        category.setSortOrder(dto.getSortOrder() == null ? 0 : dto.getSortOrder());
        category.setStatus(dto.getStatus() == null ? 1 : dto.getStatus());
        category.setRemark(dto.getRemark());
        category.setLevelNo(parent == null ? 1 : (parent.getLevelNo() == null ? 1 : parent.getLevelNo() + 1));
        category.setCreateTime(LocalDateTime.now());
        category.setUpdateTime(LocalDateTime.now());
        this.save(category);
        // 物化路径依赖自增主键，插入后回填
        category.setPath(ReagentWarehouseGuard.childPath(parent == null ? null : parent.getPath(),
                category.getId()));
        categoryMapper.updateById(category);
        log.info("新增分类: code={}, name={}, parentId={}, hazard={}",
                category.getCategoryCode(), category.getCategoryName(), parentId, category.getHazardCategory());
        return category;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ReagentCategory update(CategoryCreateDTO dto) {
        if (dto == null || dto.getId() == null) {
            throw new BusinessException("分类 ID 不能为空");
        }
        ReagentCategory category = this.getById(dto.getId());
        if (category == null) {
            throw new BusinessException("分类不存在: " + dto.getId());
        }
        // 不允许调整父级（物化路径与层级联动，避免子树路径失效；需要时请删除重建）
        if (dto.getParentId() != null && !dto.getParentId().equals(category.getParentId())) {
            throw new BusinessException("不允许调整分类父级，如需调整请删除后重建");
        }
        if (dto.getCategoryCode() != null && !dto.getCategoryCode().trim().isEmpty()
                && !dto.getCategoryCode().trim().equals(category.getCategoryCode())) {
            Long existed = this.count(new LambdaQueryWrapper<ReagentCategory>()
                    .eq(ReagentCategory::getCategoryCode, dto.getCategoryCode().trim())
                    .ne(ReagentCategory::getId, category.getId()));
            if (existed > 0) {
                throw new BusinessException("分类编码已存在: " + dto.getCategoryCode().trim());
            }
            category.setCategoryCode(dto.getCategoryCode().trim());
        }
        if (dto.getCategoryName() != null && !dto.getCategoryName().trim().isEmpty()) {
            category.setCategoryName(dto.getCategoryName().trim());
        }
        if (dto.getHazardCategory() != null) {
            category.setHazardCategory(parseHazard(dto.getHazardCategory()));
        }
        if (dto.getSortOrder() != null) {
            category.setSortOrder(dto.getSortOrder());
        }
        if (dto.getStatus() != null) {
            category.setStatus(dto.getStatus());
        }
        if (dto.getRemark() != null) {
            category.setRemark(dto.getRemark());
        }
        category.setUpdateTime(LocalDateTime.now());
        categoryMapper.updateById(category);
        return category;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        ReagentCategory category = this.getById(id);
        if (category == null) {
            throw new BusinessException("分类不存在: " + id);
        }
        if (category.getParentId() != null && category.getParentId() == 0L && hasRootChildren(id)) {
            throw new BusinessException("存在子分类，禁止删除");
        }
        Long children = this.count(new LambdaQueryWrapper<ReagentCategory>()
                .eq(ReagentCategory::getParentId, id));
        if (children > 0) {
            throw new BusinessException("存在子分类，禁止删除");
        }
        Long reagentRefs = reagentMapper.selectCount(new LambdaQueryWrapper<Reagent>()
                .eq(Reagent::getCategoryId, id));
        if (reagentRefs > 0) {
            throw new BusinessException("该分类被 " + reagentRefs + " 个试剂引用，禁止删除");
        }
        this.removeById(id);
        log.info("删除分类: id={}, name={}", id, category.getCategoryName());
    }

    @Override
    public Page<ReagentCategory> queryPage(long current, long size, String keyword, Integer status,
                                           Integer hazardCategory) {
        LambdaQueryWrapper<ReagentCategory> w = new LambdaQueryWrapper<>();
        if (keyword != null && !keyword.trim().isEmpty()) {
            String kw = keyword.trim();
            w.and(q -> q.like(ReagentCategory::getCategoryName, kw)
                    .or().like(ReagentCategory::getCategoryCode, kw));
        }
        if (status != null) {
            w.eq(ReagentCategory::getStatus, status);
        }
        if (hazardCategory != null) {
            w.eq(ReagentCategory::getHazardCategory, hazardCategory);
        }
        w.orderByAsc(ReagentCategory::getSortOrder).orderByAsc(ReagentCategory::getId);
        return this.page(new Page<>(current, size), w);
    }

    private boolean hasRootChildren(Long id) {
        return this.count(new LambdaQueryWrapper<ReagentCategory>()
                .eq(ReagentCategory::getParentId, id)) > 0;
    }

    /**
     * DTO 中 hazardCategory 为字符串（兼容前端传 "0"/"1"），解析为 SMALLINT。
     */
    private int parseHazard(String hazardCategory) {
        if (hazardCategory == null || hazardCategory.trim().isEmpty()) {
            return 0;
        }
        try {
            int v = Integer.parseInt(hazardCategory.trim());
            return v == 1 ? 1 : 0;
        } catch (NumberFormatException e) {
            return 0;
        }
    }
}
