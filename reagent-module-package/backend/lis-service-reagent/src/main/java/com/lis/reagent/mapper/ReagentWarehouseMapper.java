package com.lis.reagent.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.lis.reagent.entity.ReagentWarehouse;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 试剂仓库 / 冰箱 / 层架 Mapper（物化路径树）。
 *
 * <p>树形查询采用 <b>物化路径（materialized path）</b> 而非递归 CTE 或 N+1 逐层查询：
 * 每个节点的 {@code path} 形如 {@code /1/5/23/}，取子树只需一次
 * {@code path LIKE '/1/5/%'} 前缀匹配，可命中 {@code idx_lis_reagent_warehouse_path} 索引。</p>
 *
 * <p>注意：前缀通配只能出现在<b>右侧</b>（likeRight），左通配会使索引失效。
 * 拼接使用 PostgreSQL 的 {@code ||} 字符串连接<b>在 SQL 内部完成</b>，
 * Java 侧仍是纯 {@code #{}} 预编译绑定，不存在字符串拼接注入风险（T-05 验收标准 ②）。</p>
 *
 * @author LIS Team
 */
@Mapper
public interface ReagentWarehouseMapper extends BaseMapper<ReagentWarehouse> {

    /**
     * 取指定路径下的整棵子树（含该节点自身，若其 path 以入参为前缀）。
     *
     * <p>调用方传入父节点的完整 {@code path}（如 {@code /1/5/}），
     * SQL 内部追加 {@code %} 做右通配前缀匹配。结果按 {@code path} + {@code sortOrder}
     * 排序，便于上层按序线性组装成树而无需二次排序。</p>
     *
     * @param path 父节点物化路径，必须以 {@code /} 结尾，不可为 null
     * @return 子树节点列表（含启用与停用），无匹配时返回空列表
     */
    @Select("SELECT * FROM lis_reagent_warehouse WHERE path LIKE #{path} || '%' "
            + "ORDER BY path ASC, sort_order ASC, id ASC")
    List<ReagentWarehouse> selectChildrenByPath(@Param("path") String path);

    /**
     * 取指定路径下启用中的子树节点 ID 列表，供库存按仓库子树聚合查询使用。
     *
     * @param path 父节点物化路径，必须以 {@code /} 结尾，不可为 null
     * @return 启用中的子树节点 ID 列表，无匹配时返回空列表
     */
    @Select("SELECT id FROM lis_reagent_warehouse WHERE path LIKE #{path} || '%' AND status = 1 "
            + "ORDER BY id ASC")
    List<Long> selectChildIdsByPath(@Param("path") String path);

    /**
     * 统计某仓库节点的直接子节点数量，用于删除前的「有子节点不可删」校验。
     *
     * @param parentId 父节点 ID，不可为 null
     * @return 直接子节点数，无数据时返回 0
     */
    @Select("SELECT COUNT(1) FROM lis_reagent_warehouse WHERE parent_id = #{parentId}")
    int countChildren(@Param("parentId") Long parentId);

    /**
     * 统计某仓库节点上仍有余量的库存条目数，用于删除前的「有库存不可删」校验。
     *
     * @param warehouseId 仓库节点 ID，不可为 null
     * @return 有余量的库存条目数，无数据时返回 0
     */
    @Select("SELECT COUNT(1) FROM lis_reagent_stock "
            + "WHERE warehouse_id = #{warehouseId} AND quantity > 0")
    int countStockOnWarehouse(@Param("warehouseId") Long warehouseId);
}
