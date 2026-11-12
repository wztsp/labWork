package com.lis.reagent.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.IService;
import com.lis.reagent.dto.SupplierCreateDTO;
import com.lis.reagent.entity.Supplier;
import com.lis.reagent.vo.PriceCompareVO;

import java.util.List;

/**
 * 供应商主数据服务（R-04，通用非试剂专属）。
 *
 * <p><b>重名校验</b>：按 {@code fn_supplier_name_norm} 语义（连续空白折叠为单空格 +
 * BTRIM + LOWER）与 DB 表达式唯一索引 {@code uk_lis_supplier_name_norm} 同口径，
 * 全角空格 / U+00A0 / Tab / 大小写差异一律视同重名。</p>
 *
 * <p><b>证照口径</b>：{@code qualStatus} 由 {@code licenseExpiry} /
 * {@code medicalCertExpiry} 计算（0 待完善 / 1 有效 / 2 已过期）；
 * 采购订单下拉只返回证照有效供应商（{@link #listSelectable()}）。</p>
 *
 * @author LIS Team
 */
public interface SupplierService extends IService<Supplier> {

    /**
     * 新增供应商（手工录入）。
     *
     * @param dto 供应商信息
     * @return 创建后的供应商
     */
    Supplier create(SupplierCreateDTO dto);

    /**
     * 更新供应商。
     *
     * @param dto 供应商信息（须含 id）
     * @return 更新后的供应商
     */
    Supplier update(SupplierCreateDTO dto);

    /**
     * 删除供应商。被试剂 / 入库单 / 订单引用时拒绝删除。
     *
     * @param id 供应商 ID
     */
    void delete(Long id);

    /**
     * 分页查询。
     *
     * @param current    页码
     * @param size       页大小
     * @param keyword    名称 / 编码 / 简称模糊，可为 null
     * @param status     启用状态，可为 null
     * @param qualStatus 资质状态，可为 null
     * @param sourceType 来源（1 手工 / 2 迁移草稿），可为 null
     * @return 分页结果
     */
    Page<Supplier> queryPage(long current, long size, String keyword, Integer status,
                             Integer qualStatus, Integer sourceType);

    /**
     * 采购下拉：证照有效且启用的供应商（R-04 验收 1）。
     *
     * @return 可选供应商列表，恒不为 null
     */
    List<Supplier> listSelectable();

    /**
     * 证照临期筛选：{@code days} 天内到期（含已过期）的启用供应商。
     *
     * @param days 提前天数，0 表示只取已过期
     * @return 临期列表，恒不为 null
     */
    List<Supplier> listExpiringWithin(int days);

    /**
     * 同试剂跨供应商历史比价（按均价升序）。
     *
     * @param reagentId 试剂 ID
     * @return 比价 VO，恒不为 null
     */
    PriceCompareVO comparePrice(Long reagentId);
}
