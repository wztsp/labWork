package com.lis.reagent.service;

import com.lis.reagent.vo.SupplierScoreVO;

import java.math.BigDecimal;
import java.util.List;

/**
 * 供应商评价体系服务（T-16 / R-11）。
 *
 * <p>评分口径：准时率聚合 {@code lis_reagent_purchase_order}，拒收率聚合
 * {@code lis_reagent_inbound}，自动分 = (100 - 拒收率) * 0.6 + 准时率 * 0.4，
 * 人工分读取 {@code lis_supplier.score}，综合分 = 自动分 * 0.7 + 人工分 * 0.3。
 * 仅统计启用供应商（{@code lis_supplier.status = 1}）。</p>
 *
 * @author LIS Team
 */
public interface SupplierEvaluateService {

    /**
     * 全部启用供应商评分排行，按综合分降序。
     *
     * @param includeManual 是否将人工分计入综合分；false 时人工分按 0 计
     * @return 评分列表，按综合分降序，恒不为 null
     */
    List<SupplierScoreVO> rank(boolean includeManual);

    /**
     * 计算单个供应商评分（含人工分）。
     *
     * @param supplierId 供应商 ID
     * @return 评分 VO，恒不为 null
     */
    SupplierScoreVO evaluate(Long supplierId);

    /**
     * 保存人工评分（写入 {@code lis_supplier.score} 字段）。
     *
     * @param supplierId  供应商 ID
     * @param manualScore 人工评分，须在 0-100 之间
     */
    void saveManualScore(Long supplierId, BigDecimal manualScore);
}