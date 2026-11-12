package com.lis.reagent.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.lis.reagent.entity.Reagent;
import com.lis.reagent.entity.ReagentOpening;
import com.lis.reagent.entity.ReagentStock;
import com.lis.reagent.mapper.ReagentInboundItemMapper;
import com.lis.reagent.mapper.ReagentMapper;
import com.lis.reagent.mapper.ReagentOpeningMapper;
import com.lis.reagent.mapper.ReagentStockMapper;
import com.lis.reagent.service.ReagentDashboardService;
import com.lis.reagent.service.ReagentStockService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class ReagentDashboardServiceImpl implements ReagentDashboardService {

    private final ReagentMapper reagentMapper;
    private final ReagentStockService stockService;
    private final ReagentStockMapper stockMapper;
    private final ReagentOpeningMapper openingMapper;
    private final ReagentInboundItemMapper inboundItemMapper;

    @Override
    public Map<String, Object> getOverview() {
        Map<String, Object> overview = new HashMap<>();

        // 试剂总数 (status=1)
        Long totalReagents = reagentMapper.selectCount(new LambdaQueryWrapper<Reagent>()
                .eq(Reagent::getStatus, 1));
        overview.put("totalReagents", totalReagents);

        // 库存总值 = Σ(stock.quantity * reagent.price_purchase)
        List<Reagent> reagents = reagentMapper.selectList(new LambdaQueryWrapper<Reagent>()
                .eq(Reagent::getStatus, 1));
        BigDecimal totalStockValue = BigDecimal.ZERO;
        for (Reagent r : reagents) {
            List<ReagentStock> stocks = stockMapper.selectList(new LambdaQueryWrapper<ReagentStock>()
                    .eq(ReagentStock::getReagentId, r.getId())
                    .eq(ReagentStock::getStatus, 1));
            BigDecimal total = stocks.stream().map(ReagentStock::getQuantity)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            if (r.getPricePurchase() != null) {
                totalStockValue = totalStockValue.add(total.multiply(r.getPricePurchase()));
            }
        }
        overview.put("totalStockValue", totalStockValue);

        // 近效期数量 (30天内)
        overview.put("nearExpiryCount", stockService.listNearExpiry(30).size());

        // 低库存数量
        overview.put("lowStockCount", stockService.listBelowSafetyStock().size());

        // 高库存数量
        overview.put("overStockCount", stockService.listOverStock().size());

        // 开瓶过期预警数量 (status=1 且 openedExpiry <= 今+7天)
        Long openedExpiryCount = openingMapper.selectCount(new LambdaQueryWrapper<ReagentOpening>()
                .eq(ReagentOpening::getStatus, 1)
                .le(ReagentOpening::getOpenedExpiry, LocalDate.now().plusDays(7)));
        overview.put("openedExpiryCount", openedExpiryCount);

        return overview;
    }

    @Override
    public List<ReagentStock> getNearExpiryList(int days) {
        return stockService.listNearExpiry(days);
    }

    @Override
    public List<Map<String, Object>> getLowStockList() {
        List<ReagentStock> alerts = stockService.listBelowSafetyStock();
        List<Map<String, Object>> result = new ArrayList<>();
        for (ReagentStock alert : alerts) {
            Map<String, Object> map = new HashMap<>();
            map.put("reagentId", alert.getReagentId());
            map.put("totalQuantity", alert.getQuantity());
            Reagent reagent = reagentMapper.selectById(alert.getReagentId());
            if (reagent != null) {
                map.put("reagentName", reagent.getReagentName());
                map.put("safetyStock", reagent.getMinStock());
            }
            result.add(map);
        }
        return result;
    }

    @Override
    public List<ReagentOpening> getOpenedExpiryList() {
        List<ReagentOpening> list = openingMapper.selectList(new LambdaQueryWrapper<ReagentOpening>()
                .eq(ReagentOpening::getStatus, 1)
                .le(ReagentOpening::getOpenedExpiry, LocalDate.now().plusDays(7))
                .orderByAsc(ReagentOpening::getOpenedExpiry));
        for (ReagentOpening o : list) {
            Reagent reagent = reagentMapper.selectById(o.getReagentId());
            if (reagent != null) {
                o.setReagentName(reagent.getReagentName());
            }
        }
        return list;
    }
}
