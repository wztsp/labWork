package com.lis.reagent.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.lis.common.core.exception.BusinessException;
import com.lis.reagent.entity.Supplier;
import com.lis.reagent.mapper.ReagentInboundMapper;
import com.lis.reagent.mapper.ReagentPurchaseOrderMapper;
import com.lis.reagent.mapper.SupplierMapper;
import com.lis.reagent.service.SupplierEvaluateService;
import com.lis.reagent.vo.SupplierScoreVO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 供应商评价体系服务实现（T-16 / R-11）。
 *
 * <p>评分口径：仅统计启用供应商（{@code lis_supplier.status = 1}）。</p>
 * <ul>
 *   <li>准时率：聚合 {@code lis_reagent_purchase_order}，准确定义见
 *       {@link ReagentPurchaseOrderMapper#selectOnTimeStatBySupplier()}；</li>
 *   <li>拒收率：聚合 {@code lis_reagent_inbound}，拒收 = {@code verify_status = 2}；</li>
 *   <li>自动分 = (100 - 拒收率) * 0.6 + 准时率 * 0.4（保留两位）；</li>
 *   <li>人工分：读取 {@code lis_supplier.score}，为空取 0；</li>
 *   <li>综合分 = 自动分 * 0.7 + 人工分 * 0.3（保留两位）。</li>
 * </ul>
 *
 * @author LIS Team
 */
@Service
@RequiredArgsConstructor
public class SupplierEvaluateServiceImpl implements SupplierEvaluateService {

    private static final BigDecimal HUNDRED = new BigDecimal("100");
    private static final BigDecimal WEIGHT_REJECT = new BigDecimal("0.6");
    private static final BigDecimal WEIGHT_ON_TIME = new BigDecimal("0.4");
    private static final BigDecimal WEIGHT_AUTO = new BigDecimal("0.7");
    private static final BigDecimal WEIGHT_MANUAL = new BigDecimal("0.3");

    private final SupplierMapper supplierMapper;
    private final ReagentPurchaseOrderMapper orderMapper;
    private final ReagentInboundMapper inboundMapper;

    @Override
    public List<SupplierScoreVO> rank(boolean includeManual) {
        List<Supplier> suppliers = supplierMapper.selectList(
                new LambdaQueryWrapper<Supplier>().eq(Supplier::getStatus, 1));
        Map<Long, Map<String, Object>> orderStats =
                indexBySupplier(orderMapper.selectOnTimeStatBySupplier());
        Map<Long, Map<String, Object>> rejectStats =
                indexBySupplier(inboundMapper.selectRejectStatBySupplier());

        List<SupplierScoreVO> result = new ArrayList<>(suppliers.size());
        for (Supplier s : suppliers) {
            result.add(build(s, orderStats.get(s.getId()), rejectStats.get(s.getId()), includeManual));
        }
        result.sort(Comparator.comparing(SupplierScoreVO::getCompositeScore).reversed());
        return result;
    }

    @Override
    public SupplierScoreVO evaluate(Long supplierId) {
        if (supplierId == null) {
            throw new BusinessException("供应商 ID 不能为空");
        }
        Supplier supplier = supplierMapper.selectById(supplierId);
        if (supplier == null) {
            throw new BusinessException("供应商不存在: " + supplierId);
        }
        Map<Long, Map<String, Object>> orderStats =
                indexBySupplier(orderMapper.selectOnTimeStatBySupplier());
        Map<Long, Map<String, Object>> rejectStats =
                indexBySupplier(inboundMapper.selectRejectStatBySupplier());
        return build(supplier, orderStats.get(supplierId), rejectStats.get(supplierId), true);
    }

    @Override
    public void saveManualScore(Long supplierId, BigDecimal manualScore) {
        if (supplierId == null) {
            throw new BusinessException("供应商 ID 不能为空");
        }
        if (manualScore == null) {
            throw new BusinessException("人工评分不能为空");
        }
        if (manualScore.compareTo(BigDecimal.ZERO) < 0 || manualScore.compareTo(HUNDRED) > 0) {
            throw new BusinessException("人工评分须在 0-100 之间");
        }
        if (supplierMapper.selectById(supplierId) == null) {
            throw new BusinessException("供应商不存在: " + supplierId);
        }
        Supplier update = new Supplier();
        update.setId(supplierId);
        update.setScore(manualScore);
        update.setUpdateTime(LocalDateTime.now());
        supplierMapper.updateById(update);
    }

    /**
     * 装配单个供应商的评分 VO。
     *
     * @param supplier     供应商实体
     * @param orderRow     该供应商的准时率统计行，可为 null（无订单）
     * @param rejectRow    该供应商的拒收率统计行，可为 null（无入库单）
     * @param includeManual 是否计入人工分
     */
    private SupplierScoreVO build(Supplier supplier, Map<String, Object> orderRow,
                                  Map<String, Object> rejectRow, boolean includeManual) {
        SupplierScoreVO vo = new SupplierScoreVO();
        vo.setSupplierId(supplier.getId());
        vo.setSupplierName(supplier.getSupplierName());

        long orderTotal = longVal(col(orderRow, "total_count", "totalCount"));
        long onTime = longVal(col(orderRow, "on_time_count", "onTimeCount"));
        long inboundTotal = longVal(col(rejectRow, "total_count", "totalCount"));
        long reject = longVal(col(rejectRow, "reject_count", "rejectCount"));
        vo.setOrderCount(orderTotal);

        Object last = col(orderRow, "last_order_date", "lastOrderDate");
        if (last instanceof LocalDate) {
            vo.setLastOrderDate((LocalDate) last);
        } else if (last instanceof java.sql.Date) {
            vo.setLastOrderDate(((java.sql.Date) last).toLocalDate());
        }

        BigDecimal onTimeRate = orderTotal == 0 ? BigDecimal.ZERO
                : BigDecimal.valueOf(onTime)
                        .divide(BigDecimal.valueOf(orderTotal), 4, RoundingMode.HALF_UP)
                        .multiply(HUNDRED);
        BigDecimal rejectRate = inboundTotal == 0 ? BigDecimal.ZERO
                : BigDecimal.valueOf(reject)
                        .divide(BigDecimal.valueOf(inboundTotal), 4, RoundingMode.HALF_UP)
                        .multiply(HUNDRED);
        vo.setOnTimeRate(onTimeRate);
        vo.setRejectRate(rejectRate);

        BigDecimal autoScore = HUNDRED.subtract(rejectRate).multiply(WEIGHT_REJECT)
                .add(onTimeRate.multiply(WEIGHT_ON_TIME))
                .setScale(2, RoundingMode.HALF_UP);
        vo.setAutoScore(autoScore);

        BigDecimal manual = includeManual && supplier.getScore() != null
                ? supplier.getScore() : BigDecimal.ZERO;
        vo.setManualScore(manual);

        BigDecimal compositeScore = autoScore.multiply(WEIGHT_AUTO)
                .add(manual.multiply(WEIGHT_MANUAL))
                .setScale(2, RoundingMode.HALF_UP);
        vo.setCompositeScore(compositeScore);
        return vo;
    }

    /**
     * 将统计行列表按下单主键 supplier_id 索引为 Map。
     */
    private Map<Long, Map<String, Object>> indexBySupplier(List<Map<String, Object>> rows) {
        Map<Long, Map<String, Object>> map = new HashMap<>();
        if (rows == null) {
            return map;
        }
        for (Map<String, Object> row : rows) {
            Object id = col(row, "supplier_id", "supplierId");
            if (id != null) {
                map.put(((Number) id).longValue(), row);
            }
        }
        return map;
    }

    /**
     * 兼容 snake_case / camelCase 两种别名（取决于全局
     * {@code map-underscore-to-camel-case} 对 Map 结果键的映射行为）。
     */
    private Object col(Map<String, Object> row, String snake, String camel) {
        if (row == null) {
            return null;
        }
        if (row.containsKey(snake)) {
            return row.get(snake);
        }
        if (row.containsKey(camel)) {
            return row.get(camel);
        }
        for (Map.Entry<String, Object> e : row.entrySet()) {
            if (e.getKey().equalsIgnoreCase(snake)) {
                return e.getValue();
            }
        }
        return null;
    }

    private long longVal(Object o) {
        return o == null ? 0L : ((Number) o).longValue();
    }
}