package com.lis.reagent.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.lis.common.core.exception.BusinessException;
import com.lis.reagent.dto.WarehouseCreateDTO;
import com.lis.reagent.entity.ReagentWarehouse;
import com.lis.reagent.enums.WhType;
import com.lis.reagent.mapper.ReagentWarehouseMapper;
import com.lis.reagent.service.ReagentWarehouseService;
import com.lis.reagent.support.ReagentWarehouseGuard;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 试剂仓库服务实现（R-09）。
 *
 * @author LIS Team
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ReagentWarehouseServiceImpl extends ServiceImpl<ReagentWarehouseMapper, ReagentWarehouse>
        implements ReagentWarehouseService {

    private final ReagentWarehouseMapper warehouseMapper;

    @Override
    public List<ReagentWarehouse> tree() {
        List<ReagentWarehouse> all = this.list(new LambdaQueryWrapper<ReagentWarehouse>()
                .orderByAsc(ReagentWarehouse::getPath)
                .orderByAsc(ReagentWarehouse::getSortOrder)
                .orderByAsc(ReagentWarehouse::getId));
        Map<Long, ReagentWarehouse> byId = new HashMap<>();
        for (ReagentWarehouse w : all) {
            w.setChildren(new ArrayList<>());
            byId.put(w.getId(), w);
        }
        List<ReagentWarehouse> roots = new ArrayList<>();
        for (ReagentWarehouse w : all) {
            ReagentWarehouse parent = w.getParentId() == null ? null : byId.get(w.getParentId());
            if (parent != null && !parent.getId().equals(w.getId())) {
                parent.getChildren().add(w);
            } else {
                roots.add(w);
            }
        }
        return roots;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ReagentWarehouse create(WarehouseCreateDTO dto) {
        if (dto == null || dto.getWhCode() == null || dto.getWhCode().trim().isEmpty()) {
            throw new BusinessException("仓库编码不能为空");
        }
        if (dto.getWhName() == null || dto.getWhName().trim().isEmpty()) {
            throw new BusinessException("仓库名称不能为空");
        }
        Long existed = this.count(new LambdaQueryWrapper<ReagentWarehouse>()
                .eq(ReagentWarehouse::getWhCode, dto.getWhCode().trim()));
        if (existed > 0) {
            throw new BusinessException("仓库编码已存在: " + dto.getWhCode().trim());
        }
        ReagentWarehouseGuard.assertTempRange(dto.getTempMin(), dto.getTempMax());

        Long parentId = dto.getParentId() == null ? 0L : dto.getParentId();
        ReagentWarehouse parent = null;
        if (parentId != 0L) {
            parent = this.getById(parentId);
            if (parent == null) {
                throw new BusinessException("父节点不存在: " + parentId);
            }
        }
        ReagentWarehouse wh = new ReagentWarehouse();
        wh.setWhCode(dto.getWhCode().trim());
        wh.setWhName(dto.getWhName().trim());
        wh.setParentId(parentId);
        wh.setWhType(dto.getWhType() == null ? WhType.STORE.getCode() : dto.getWhType());
        wh.setDeptId(dto.getDeptId());
        wh.setDeptName(dto.getDeptName());
        wh.setTempMin(dto.getTempMin());
        wh.setTempMax(dto.getTempMax());
        wh.setLocationDesc(dto.getLocationDesc());
        wh.setSortOrder(dto.getSortOrder() == null ? 0 : dto.getSortOrder());
        wh.setStatus(dto.getStatus() == null ? 1 : dto.getStatus());
        wh.setRemark(dto.getRemark());
        wh.setCreateTime(LocalDateTime.now());
        wh.setUpdateTime(LocalDateTime.now());
        this.save(wh);
        // 物化路径依赖自增主键，插入后回填
        wh.setPath(ReagentWarehouseGuard.childPath(parent == null ? null : parent.getPath(), wh.getId()));
        warehouseMapper.updateById(wh);
        log.info("新增仓库节点: code={}, name={}, parentId={}, type={}",
                wh.getWhCode(), wh.getWhName(), parentId, wh.getWhType());
        return wh;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ReagentWarehouse update(WarehouseCreateDTO dto) {
        if (dto == null || dto.getId() == null) {
            throw new BusinessException("仓库 ID 不能为空");
        }
        ReagentWarehouse wh = this.getById(dto.getId());
        if (wh == null) {
            throw new BusinessException("仓库节点不存在: " + dto.getId());
        }
        if (dto.getParentId() != null && !dto.getParentId().equals(wh.getParentId())) {
            throw new BusinessException("不允许调整仓库父级，如需调整请删除后重建");
        }
        if (dto.getWhCode() != null && !dto.getWhCode().trim().isEmpty()
                && !dto.getWhCode().trim().equals(wh.getWhCode())) {
            Long existed = this.count(new LambdaQueryWrapper<ReagentWarehouse>()
                    .eq(ReagentWarehouse::getWhCode, dto.getWhCode().trim())
                    .ne(ReagentWarehouse::getId, wh.getId()));
            if (existed > 0) {
                throw new BusinessException("仓库编码已存在: " + dto.getWhCode().trim());
            }
            wh.setWhCode(dto.getWhCode().trim());
        }
        if (dto.getWhName() != null && !dto.getWhName().trim().isEmpty()) {
            wh.setWhName(dto.getWhName().trim());
        }
        if (dto.getWhType() != null) {
            wh.setWhType(dto.getWhType());
        }
        if (dto.getDeptId() != null) {
            wh.setDeptId(dto.getDeptId());
        }
        if (dto.getDeptName() != null) {
            wh.setDeptName(dto.getDeptName());
        }
        BigDecimal tempMin = dto.getTempMin() == null ? wh.getTempMin() : dto.getTempMin();
        BigDecimal tempMax = dto.getTempMax() == null ? wh.getTempMax() : dto.getTempMax();
        ReagentWarehouseGuard.assertTempRange(tempMin, tempMax);
        wh.setTempMin(tempMin);
        wh.setTempMax(tempMax);
        if (dto.getLocationDesc() != null) {
            wh.setLocationDesc(dto.getLocationDesc());
        }
        if (dto.getSortOrder() != null) {
            wh.setSortOrder(dto.getSortOrder());
        }
        if (dto.getStatus() != null) {
            wh.setStatus(dto.getStatus());
        }
        if (dto.getRemark() != null) {
            wh.setRemark(dto.getRemark());
        }
        wh.setUpdateTime(LocalDateTime.now());
        warehouseMapper.updateById(wh);
        return wh;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        if (id != null && id == ReagentWarehouseGuard.DEFAULT_WAREHOUSE_ID) {
            throw new BusinessException("系统内置默认库房（id=0）不可删除");
        }
        ReagentWarehouse wh = this.getById(id);
        if (wh == null) {
            throw new BusinessException("仓库节点不存在: " + id);
        }
        int children = warehouseMapper.countChildren(id);
        if (children > 0) {
            throw new BusinessException("存在子节点，禁止删除");
        }
        int stockCount = warehouseMapper.countStockOnWarehouse(id);
        if (stockCount > 0) {
            throw new BusinessException("该节点上仍有 " + stockCount + " 条有余量库存，禁止删除");
        }
        this.removeById(id);
        log.info("删除仓库节点: id={}, name={}", id, wh.getWhName());
    }

    @Override
    public Page<ReagentWarehouse> queryPage(long current, long size, String keyword, Integer status, Integer whType) {
        LambdaQueryWrapper<ReagentWarehouse> w = new LambdaQueryWrapper<>();
        if (keyword != null && !keyword.trim().isEmpty()) {
            String kw = keyword.trim();
            w.and(q -> q.like(ReagentWarehouse::getWhName, kw)
                    .or().like(ReagentWarehouse::getWhCode, kw));
        }
        if (status != null) {
            w.eq(ReagentWarehouse::getStatus, status);
        }
        if (whType != null) {
            w.eq(ReagentWarehouse::getWhType, whType);
        }
        w.orderByAsc(ReagentWarehouse::getPath).orderByAsc(ReagentWarehouse::getSortOrder);
        return this.page(new Page<>(current, size), w);
    }

    @Override
    public List<Long> subtreeIds(Long id) {
        // null → 默认库房 0；哨兵节点 path='/0/'，子树查询天然包含自身
        long wh = ReagentWarehouseGuard.normalize(id);
        ReagentWarehouse node = this.getById(wh);
        if (node == null || node.getPath() == null || node.getPath().isEmpty()) {
            return java.util.Collections.singletonList(wh);
        }
        return warehouseMapper.selectChildIdsByPath(node.getPath());
    }
}
