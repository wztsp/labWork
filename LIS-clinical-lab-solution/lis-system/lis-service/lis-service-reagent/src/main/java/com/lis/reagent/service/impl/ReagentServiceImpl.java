package com.lis.reagent.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.lis.common.core.exception.BusinessException;
import com.lis.reagent.entity.Reagent;
import com.lis.reagent.entity.ReagentConsumption;
import com.lis.reagent.entity.ReagentInboundItem;
import com.lis.reagent.entity.ReagentOpening;
import com.lis.reagent.entity.ReagentPurchaseApplyItem;
import com.lis.reagent.entity.ReagentPurchaseOrderItem;
import com.lis.reagent.entity.ReagentRequisitionItem;
import com.lis.reagent.entity.ReagentStock;
import com.lis.reagent.enums.StockStatus;
import com.lis.reagent.mapper.ReagentConsumptionMapper;
import com.lis.reagent.mapper.ReagentInboundItemMapper;
import com.lis.reagent.mapper.ReagentMapper;
import com.lis.reagent.mapper.ReagentOpeningMapper;
import com.lis.reagent.mapper.ReagentPurchaseApplyItemMapper;
import com.lis.reagent.mapper.ReagentPurchaseOrderItemMapper;
import com.lis.reagent.mapper.ReagentRequisitionItemMapper;
import com.lis.reagent.mapper.ReagentStockMapper;
import com.lis.reagent.service.ReagentService;
import com.lis.reagent.vo.PurchaseSuggestionDTO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

@Slf4j
@Service
@RequiredArgsConstructor
public class ReagentServiceImpl extends ServiceImpl<ReagentMapper, Reagent> implements ReagentService {

    private final ReagentMapper reagentMapper;
    private final ReagentStockMapper stockMapper;
    private final ReagentPurchaseApplyItemMapper applyItemMapper;
    private final ReagentPurchaseOrderItemMapper orderItemMapper;
    private final ReagentRequisitionItemMapper requisitionItemMapper;
    private final ReagentInboundItemMapper inboundItemMapper;
    private final ReagentConsumptionMapper consumptionMapper;
    private final ReagentOpeningMapper openingMapper;

    @Override
    public Page<Reagent> queryPage(long current, long size, String keyword, Long instrumentId, Long testItemId) {
        return queryPage(current, size, keyword, null, null, null, null, null, instrumentId, testItemId);
    }

    @Override
    public Page<Reagent> queryPage(long current, long size, String keyword, String reagentCode,
                                   String reagentName, Integer status, Long categoryId,
                                   Integer hazardFlag, Long instrumentId, Long testItemId) {
        return queryPage(current, size, keyword, reagentCode, reagentName, status, categoryId,
                hazardFlag, instrumentId, testItemId,
                null, null, null, null, null, null);
    }

    @Override
    public Page<Reagent> queryPage(long current, long size, String keyword, String reagentCode,
                                   String reagentName, Integer status, Long categoryId,
                                   Integer hazardFlag, Long instrumentId, Long testItemId,
                                   BigDecimal minQuantity, BigDecimal maxQuantity,
                                   LocalDate expiryStart, LocalDate expiryEnd,
                                   Long warehouseId, String location) {
        LambdaQueryWrapper<Reagent> wrapper = new LambdaQueryWrapper<>();
        if (keyword != null && !keyword.isEmpty()) {
            wrapper.and(w -> w.like(Reagent::getReagentCode, keyword)
                    .or().like(Reagent::getReagentName, keyword));
        }
        if (reagentCode != null && !reagentCode.isEmpty()) {
            wrapper.eq(Reagent::getReagentCode, reagentCode);
        }
        if (reagentName != null && !reagentName.isEmpty()) {
            wrapper.like(Reagent::getReagentName, reagentName);
        }
        if (status != null) {
            wrapper.eq(Reagent::getStatus, status);
        }
        if (categoryId != null) {
            wrapper.eq(Reagent::getCategoryId, categoryId);
        }
        if (hazardFlag != null) {
            wrapper.eq(Reagent::getHazardFlag, hazardFlag);
        }
        if (instrumentId != null) {
            wrapper.eq(Reagent::getInstrumentId, instrumentId);
        }
        if (testItemId != null) {
            wrapper.eq(Reagent::getTestItemId, testItemId);
        }

        // T-19 / R-14 库存维度组合条件：经 lis_reagent_stock 反查试剂 ID 收敛
        Set<Long> stockMatchedIds = resolveStockMatchedReagentIds(
                minQuantity, maxQuantity, expiryStart, expiryEnd, warehouseId, location);
        if (stockMatchedIds != null) {
            if (stockMatchedIds.isEmpty()) {
                // 任一库存维度命中为空 → 直接返回空页
                return new Page<>(current, size);
            }
            wrapper.in(Reagent::getId, stockMatchedIds);
        }

        wrapper.orderByDesc(Reagent::getCreateTime);
        return this.page(new Page<>(current, size), wrapper);
    }

    /**
     * 解析库存维度命中的试剂 ID 集合。
     *
     * <p>返回 {@code null} 表示未启用任何库存维度条件（无需收敛）；
     * 返回空集合表示有库存条件但无命中（结果为空页）；
     * 否则为各库存维度交集后的试剂 ID 集合。</p>
     */
    private Set<Long> resolveStockMatchedReagentIds(BigDecimal minQuantity, BigDecimal maxQuantity,
                                                    LocalDate expiryStart, LocalDate expiryEnd,
                                                    Long warehouseId, String location) {
        boolean quantityActive = minQuantity != null || maxQuantity != null;
        boolean batchActive = expiryStart != null || expiryEnd != null
                || warehouseId != null || (location != null && !location.isEmpty());
        if (!quantityActive && !batchActive) {
            return null;
        }

        Set<Long> result = null; // null 表示尚未初始化

        // 1) 库存量区间（聚合 HAVING）
        if (quantityActive) {
            List<Long> ids = stockMapper.selectReagentIdsByQuantityRange(minQuantity, maxQuantity);
            result = initIntersection(result, new HashSet<>(ids));
        }

        // 2) 批次维度（效期 / 仓库 / 位置）
        if (batchActive) {
            LambdaQueryWrapper<ReagentStock> sw = new LambdaQueryWrapper<>();
            if (expiryStart != null) {
                sw.ge(ReagentStock::getExpiryDate, expiryStart);
            }
            if (expiryEnd != null) {
                sw.le(ReagentStock::getExpiryDate, expiryEnd);
            }
            if (warehouseId != null) {
                sw.eq(ReagentStock::getWarehouseId, warehouseId);
            }
            if (location != null && !location.isEmpty()) {
                sw.like(ReagentStock::getLocation, location);
            }
            List<ReagentStock> stocks = stockMapper.selectList(sw);
            Set<Long> ids = new HashSet<>();
            for (ReagentStock s : stocks) {
                if (s.getReagentId() != null) {
                    ids.add(s.getReagentId());
                }
            }
            result = initIntersection(result, ids);
        }

        return result == null ? Set.of() : result;
    }

    /** 求交集：首次初始化直接返回 newSet，否则保留交集。 */
    private Set<Long> initIntersection(Set<Long> acc, Set<Long> newSet) {
        if (acc == null) {
            return newSet;
        }
        acc.retainAll(newSet);
        return acc;
    }

    @Override
    public Reagent getByCode(String reagentCode) {
        return this.getOne(new LambdaQueryWrapper<Reagent>()
                .eq(Reagent::getReagentCode, reagentCode));
    }

    @Override
    public PurchaseSuggestionDTO getPurchaseSuggestion(Long reagentId) {
        Reagent reagent = this.getById(reagentId);
        if (reagent == null) {
            throw new BusinessException("试剂不存在: " + reagentId);
        }
        // 当前库存：未过期且可用的批次汇总（无到期日的批次视为未过期）
        List<ReagentStock> stocks = stockMapper.selectList(new LambdaQueryWrapper<ReagentStock>()
                .eq(ReagentStock::getReagentId, reagentId)
                .eq(ReagentStock::getStatus, StockStatus.AVAILABLE.getCode())
                .and(w -> w.isNull(ReagentStock::getExpiryDate)
                        .or().ge(ReagentStock::getExpiryDate, LocalDate.now())));
        BigDecimal currentStock = stocks.stream()
                .map(ReagentStock::getQuantity)
                .filter(Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        // 目标库存：优先最佳库存，未配置则取最高库存；均未配置时建议数量为 0
        BigDecimal targetStock = reagent.getOptimalStock() != null
                ? reagent.getOptimalStock() : reagent.getMaxStock();
        BigDecimal suggestedQty = targetStock == null
                ? BigDecimal.ZERO
                : targetStock.subtract(currentStock).max(BigDecimal.ZERO);

        int cycleDays = reagent.getPurchaseCycleDays() == null ? 0 : reagent.getPurchaseCycleDays();
        PurchaseSuggestionDTO dto = new PurchaseSuggestionDTO();
        dto.setCurrentStock(currentStock);
        dto.setMinStock(reagent.getMinStock());
        dto.setMaxStock(reagent.getMaxStock());
        dto.setOptimalStock(reagent.getOptimalStock());
        dto.setPurchaseCycleDays(reagent.getPurchaseCycleDays());
        dto.setSuggestedQty(suggestedQty);
        dto.setSuggestedDate(LocalDate.now().plusDays(cycleDays));
        return dto;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        Reagent reagent = this.getById(id);
        if (reagent == null) {
            throw new BusinessException("试剂不存在: " + id);
        }
        // 引用守卫：被库存 / 业务单明细引用时禁止物理删除（避免孤儿数据），建议改为停用
        Long stockRefs = stockMapper.selectCount(new LambdaQueryWrapper<ReagentStock>()
                .eq(ReagentStock::getReagentId, id));
        Long applyItemRefs = applyItemMapper.selectCount(new LambdaQueryWrapper<ReagentPurchaseApplyItem>()
                .eq(ReagentPurchaseApplyItem::getReagentId, id));
        Long orderItemRefs = orderItemMapper.selectCount(new LambdaQueryWrapper<ReagentPurchaseOrderItem>()
                .eq(ReagentPurchaseOrderItem::getReagentId, id));
        Long reqItemRefs = requisitionItemMapper.selectCount(new LambdaQueryWrapper<ReagentRequisitionItem>()
                .eq(ReagentRequisitionItem::getReagentId, id));
        Long inboundItemRefs = inboundItemMapper.selectCount(new LambdaQueryWrapper<ReagentInboundItem>()
                .eq(ReagentInboundItem::getReagentId, id));
        Long consumptionRefs = consumptionMapper.selectCount(new LambdaQueryWrapper<ReagentConsumption>()
                .eq(ReagentConsumption::getReagentId, id));
        Long openingRefs = openingMapper.selectCount(new LambdaQueryWrapper<ReagentOpening>()
                .eq(ReagentOpening::getReagentId, id));
        if (stockRefs + applyItemRefs + orderItemRefs + reqItemRefs + inboundItemRefs
                + consumptionRefs + openingRefs > 0) {
            throw new BusinessException("该试剂已被引用（库存 "
                    + stockRefs + " 条、采购申请明细 " + applyItemRefs + " 条、采购订单明细 "
                    + orderItemRefs + " 条、请领明细 " + reqItemRefs + " 条、入库明细 "
                    + inboundItemRefs + " 条、消耗 " + consumptionRefs + " 条、期初 " + openingRefs
                    + " 条），不可删除；请改为停用（status=0）");
        }
        this.removeById(id);
        log.info("删除试剂: id={}, code={}, name={}", id, reagent.getReagentCode(), reagent.getReagentName());
    }
}
