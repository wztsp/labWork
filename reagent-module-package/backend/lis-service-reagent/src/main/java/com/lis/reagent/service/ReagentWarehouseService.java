package com.lis.reagent.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.IService;
import com.lis.reagent.dto.WarehouseCreateDTO;
import com.lis.reagent.entity.ReagentWarehouse;

import java.util.List;

/**
 * 试剂仓库 / 冰箱 / 层架服务（R-09，三级树）。
 *
 * <p><b>默认库房哨兵</b>：{@code id=0} 为系统内置「默认库房（待归集）」，
 * 存量库存 {@code warehouse_id=0} 即默认库房。查询 / 操作按 {@code warehouse_id = 0}
 * 处理存量，<b>禁止 IS NULL</b>（PostgreSQL 唯一索引中 NULL &lt;&gt; NULL，见 C-2）。</p>
 *
 * <p><b>物化路径</b>：{@code path} 如 {@code /1/5/23/}，子树查询一条
 * {@code path LIKE '/1/5/%'} 搞定，避免递归 N+1（T-05 已实现 {@code selectChildrenByPath}）。</p>
 *
 * @author LIS Team
 */
public interface ReagentWarehouseService extends IService<ReagentWarehouse> {

    /**
     * 仓库树（库房 → 冰箱 → 层架）。
     *
     * @return 根节点列表（含 children 子树），恒不为 null
     */
    List<ReagentWarehouse> tree();

    /**
     * 新增仓库节点。
     *
     * @param dto 节点信息（含父级 / 类型 / 温度区间）
     * @return 创建后的节点
     */
    ReagentWarehouse create(WarehouseCreateDTO dto);

    /**
     * 更新仓库节点（不允许调整父级；如需调整请删除重建）。
     *
     * @param dto 节点信息（须含 id）
     * @return 更新后的节点
     */
    ReagentWarehouse update(WarehouseCreateDTO dto);

    /**
     * 删除仓库节点。默认库房（id=0）不可删；有子节点 / 有余量库存时拒绝。
     *
     * @param id 节点 ID
     */
    void delete(Long id);

    /**
     * 分页查询。
     *
     * @param current 页码
     * @param size    页大小
     * @param keyword 编码 / 名称模糊，可为 null
     * @param status  状态，可为 null
     * @param whType  类型（1 库房 / 2 冰箱 / 3 层架），可为 null
     * @return 分页结果
     */
    Page<ReagentWarehouse> queryPage(long current, long size, String keyword, Integer status, Integer whType);

    /**
     * 取某节点子树（含自身）的启用 ID 列表，供库存按仓库聚合查询。
     *
     * @param id 节点 ID，可为 null（按默认库房 0 处理）
     * @return 子树节点 ID 列表，恒不为 null
     */
    List<Long> subtreeIds(Long id);
}
