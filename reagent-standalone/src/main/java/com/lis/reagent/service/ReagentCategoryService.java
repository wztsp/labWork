package com.lis.reagent.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.IService;
import com.lis.reagent.dto.CategoryCreateDTO;
import com.lis.reagent.entity.ReagentCategory;

import java.util.List;

/**
 * 试剂分类字典服务（R-06，树形）。
 *
 * <p><b>树形</b>：采用 {@code path} 物化路径（如 {@code /1/5/}），子树查询一条
 * {@code LIKE} 搞定，避免递归 N+1。</p>
 *
 * <p><b>危险品</b>：{@code lis_reagent_category.hazard_category}
 * （0 普通 / 1 危险品类）列已在 T-01 落地，直接复用；该类下试剂默认视为危险品
 * （{@code lis_reagent.hazard_flag} 由试剂维护）。<b>不缺列，无需改 DDL。</b></p>
 *
 * @author LIS Team
 */
public interface ReagentCategoryService extends IService<ReagentCategory> {

    /**
     * 分类树（按 sortOrder 排序组装）。
     *
     * @return 根节点列表（含 children 子树），恒不为 null
     */
    List<ReagentCategory> tree();

    /**
     * 新增分类。
     *
     * @param dto 分类信息（含父级 / 编码 / 危险品标志）
     * @return 创建后的分类
     */
    ReagentCategory create(CategoryCreateDTO dto);

    /**
     * 更新分类（不允许调整父级；如需调整请删除重建）。
     *
     * @param dto 分类信息（须含 id）
     * @return 更新后的分类
     */
    ReagentCategory update(CategoryCreateDTO dto);

    /**
     * 删除分类。存在子节点或被试剂引用时拒绝（R-06 验收 2）。
     *
     * @param id 分类 ID
     */
    void delete(Long id);

    /**
     * 分页查询。
     *
     * @param current        页码
     * @param size           页大小
     * @param keyword        编码 / 名称模糊，可为 null
     * @param status         状态，可为 null
     * @param hazardCategory 危险品标志（0 / 1），可为 null
     * @return 分页结果
     */
    Page<ReagentCategory> queryPage(long current, long size, String keyword, Integer status,
                                    Integer hazardCategory);
}
