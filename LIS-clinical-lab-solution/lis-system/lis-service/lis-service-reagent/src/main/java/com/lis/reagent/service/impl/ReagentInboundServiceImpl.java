package com.lis.reagent.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.lis.common.core.exception.BusinessException;
import com.lis.reagent.dto.StockOpContext;
import com.lis.reagent.entity.Reagent;
import com.lis.reagent.entity.ReagentInbound;
import com.lis.reagent.entity.ReagentInboundItem;
import com.lis.reagent.entity.ReagentPurchaseOrder;
import com.lis.reagent.entity.ReagentStock;
import com.lis.reagent.enums.OrderStatus;
import com.lis.reagent.enums.RecordType;
import com.lis.reagent.mapper.ReagentInboundItemMapper;
import com.lis.reagent.mapper.ReagentInboundMapper;
import com.lis.reagent.mapper.ReagentMapper;
import com.lis.reagent.mapper.ReagentPurchaseOrderItemMapper;
import com.lis.reagent.mapper.ReagentPurchaseOrderMapper;
import com.lis.reagent.mapper.ReagentStockMapper;
import com.lis.reagent.service.ReagentBudgetService;
import com.lis.reagent.service.ReagentInboundService;
import com.lis.reagent.service.ReagentStockService;
import com.lis.reagent.support.BizNoGenerator;
import com.lis.reagent.support.ReagentWarehouseGuard;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class ReagentInboundServiceImpl extends ServiceImpl<ReagentInboundMapper, ReagentInbound>
        implements ReagentInboundService {

    /** 入库单号前缀（R-05 统一单号：INB + yyyyMMdd + 4位序号） */
    private static final String NO_PREFIX = "INB";

    private final ReagentInboundItemMapper inboundItemMapper;
    private final ReagentStockService stockService;
    private final ReagentMapper reagentMapper;
    private final ReagentPurchaseOrderMapper orderMapper;
    private final ReagentPurchaseOrderItemMapper orderItemMapper;
    private final ReagentStockMapper stockMapper;
    private final ReagentBudgetService budgetService;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ReagentInbound createInbound(ReagentInbound inbound) {
        inbound.setInboundNo(BizNoGenerator.nextNo(NO_PREFIX, this::selectMaxInboundNo));
        inbound.setVerifyStatus(0);
        inbound.setCreateTime(LocalDateTime.now());

        BigDecimal totalAmount = BigDecimal.ZERO;
        List<ReagentInboundItem> items = inbound.getItems();
        if (items != null) {
            for (ReagentInboundItem item : items) {
                if (item.getAmount() == null && item.getPurchasePrice() != null && item.getQuantity() != null) {
                    item.setAmount(item.getPurchasePrice().multiply(item.getQuantity()));
                }
                if (item.getAmount() != null) {
                    totalAmount = totalAmount.add(item.getAmount());
                }
                item.setCreateTime(LocalDateTime.now());
            }
        }
        inbound.setTotalAmount(totalAmount);

        this.save(inbound);

        if (items != null) {
            for (ReagentInboundItem item : items) {
                item.setInboundId(inbound.getId());
                inboundItemMapper.insert(item);
            }
        }
        log.info("创建入库单: inboundNo={}, itemCount={}, orderId={}",
                inbound.getInboundNo(), items == null ? 0 : items.size(), inbound.getOrderId());
        return inbound;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ReagentInbound verifyInbound(Long id, Long verifierId, String verifierName) {
        log.info("[入库验收开始] id={}, verifierId={}, verifierName={}", id, verifierId, verifierName);
        if (verifierId == null) {
            throw new BusinessException("验收入缺少 verifierId");
        }
        if (verifierName == null || verifierName.trim().isEmpty()) {
            throw new BusinessException("验收入缺少 verifierName");
        }
        ReagentInbound inbound = this.getById(id);
        if (inbound == null) {
            throw new BusinessException("入库单不存在: " + id);
        }
        // 幂等：已验收通过的直接返回，避免前端/用户误以为失败重试点爆日志 + 制造"自动验收"错觉
        if (inbound.getVerifyStatus() != null && inbound.getVerifyStatus() == 1) {
            log.warn("[入库验收-幂等命中] 入库单已验收通过，直接返回: id={}, inboundNo={}", id, inbound.getInboundNo());
            return inbound;
        }
        if (inbound.getVerifyStatus() == null || inbound.getVerifyStatus() != 0) {
            throw new BusinessException("入库单状态不允许验收, 当前状态: " + inbound.getVerifyStatus());
        }
        inbound.setVerifyStatus(1);
        inbound.setVerifierId(verifierId);
        inbound.setVerifierName(verifierName.trim());
        inbound.setVerifyTime(LocalDateTime.now());
        this.updateById(inbound);

        StockOpContext ctx = StockOpContext.builder()
                .operatorId(verifierId)
                .operatorName(verifierName.trim())
                .sourceNo(inbound.getInboundNo())
                .relatedType("INBOUND")
                .relatedId(inbound.getId())
                .remark("入库验收: " + inbound.getInboundNo())
                .build();

        // 将明细写入库存（C-7：仓库传递链路补全，warehouseId 归一化后传给 inStock）
        List<ReagentInboundItem> items = inboundItemMapper.selectList(
                new LambdaQueryWrapper<ReagentInboundItem>().eq(ReagentInboundItem::getInboundId, id));
        if (items == null || items.isEmpty()) {
            log.warn("[入库验收-无明细] id={}, inboundNo={}, 未生成库存记录", id, inbound.getInboundNo());
        } else {
            for (ReagentInboundItem item : items) {
                if (item.getId() == null) {
                    throw new BusinessException("入库明细主键未回填，无法生成条码: inboundId=" + id);
                }
                if (item.getReagentId() == null) {
                    throw new BusinessException("入库明细缺少 reagentId: inboundItemId=" + item.getId());
                }
                if (item.getQuantity() == null || item.getQuantity().compareTo(BigDecimal.ZERO) <= 0) {
                    throw new BusinessException("入库明细数量异常: inboundItemId=" + item.getId());
                }
                // 入库验收自动生成条码：明细已有条码则保留，否则按 RG+试剂ID+批号+明细ID 生成唯一值
                // 生成后同时回写到入库明细，确保入库详情页可直接查询条码
                String barcode = genBarcode(item);
                String current = item.getBarcode() == null ? "" : item.getBarcode().trim();
                if (!barcode.equals(current)) {
                    item.setBarcode(barcode);
                    inboundItemMapper.updateById(item);
                }

                ReagentStock stock = new ReagentStock();
                stock.setReagentId(item.getReagentId());
                stock.setLotNo(item.getLotNo());
                stock.setBarcode(barcode);
                stock.setExpiryDate(item.getExpiryDate());
                stock.setLocation(item.getLocation());
                // C-7：仓库必须随入库传递，否则全部落默认仓
                stock.setWarehouseId(ReagentWarehouseGuard.normalize(item.getWarehouseId()));
                stockService.inStockTyped(stock, item.getQuantity(), RecordType.IN.getCode(), ctx);

                // C-4：orderItemId 精确回写 arrivedQty，CAS 防超收（受影响行数必须为 1）
                if (item.getOrderItemId() != null) {
                    int rows = orderItemMapper.receiveQty(item.getOrderItemId(), item.getQuantity());
                    if (rows != 1) {
                        throw new BusinessException("到货数量超过订单量，验收失败回滚: orderItemId="
                                + item.getOrderItemId());
                    }
                }
            }
        }

        // 订单状态推进 + 全到齐后预算实扣
        advanceOrder(inbound, ctx);
        log.info("[入库验收成功] inboundNo={}, itemCount={}, verifierId={}",
                inbound.getInboundNo(), items == null ? 0 : items.size(), verifierId);
        return inbound;
    }

    /**
     * 生成 / 规整库存条码：明细已填条码则原样使用，否则自动生成唯一值
     * （RG + 试剂ID + 批号 + 明细ID），确保移动端扫码可命中。
     */
    private String genBarcode(ReagentInboundItem item) {
        String bar = item.getBarcode();
        if (bar != null && !bar.trim().isEmpty()) {
            return bar.trim();
        }
        String lot = item.getLotNo() == null ? "NOLOT" : item.getLotNo().trim();
        lot = lot.replaceAll("[^A-Za-z0-9]", "");
        if (lot.isEmpty()) {
            lot = "NOLOT";
        }
        return "RG" + item.getReagentId() + "-" + lot + "-" + item.getId();
    }

    /**
     * 推进关联订单状态：部分到货 / 已到货；全到齐后触发预算实扣。
     */
    private void advanceOrder(ReagentInbound inbound, StockOpContext ctx) {
        if (inbound.getOrderId() == null) {
            return;
        }
        ReagentPurchaseOrder order = orderMapper.selectByIdForUpdate(inbound.getOrderId());
        if (order == null) {
            log.warn("入库单关联订单不存在: orderId={}", inbound.getOrderId());
            return;
        }
        OrderStatus current = OrderStatus.of(order.getStatus());
        if (current == OrderStatus.CANCELLED || current == OrderStatus.ARRIVED) {
            throw new BusinessException("订单已" + (current == OrderStatus.CANCELLED ? "取消" : "到货")
                    + "，不允许继续验收");
        }
        int unreceived = orderItemMapper.countUnreceivedByOrderId(order.getId());
        if (unreceived <= 0) {
            order.setStatus(OrderStatus.ARRIVED.getCode());
            orderMapper.updateById(order);
            // 订单全部到货 → 预算实扣（幂等：同一订单只实扣一次）
            budgetService.settleForOrder(order, ctx);
        } else {
            order.setStatus(OrderStatus.PARTIAL_ARRIVED.getCode());
            orderMapper.updateById(order);
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ReagentInbound rejectInbound(Long id, Long verifierId, String verifierName, String reason) {
        ReagentInbound inbound = this.getById(id);
        if (inbound == null) {
            throw new BusinessException("入库单不存在: " + id);
        }
        if (inbound.getVerifyStatus() != 0) {
            throw new BusinessException("入库单状态不允许拒收, 当前状态: " + inbound.getVerifyStatus());
        }
        inbound.setVerifyStatus(2);
        inbound.setVerifierId(verifierId);
        inbound.setVerifierName(verifierName);
        inbound.setVerifyTime(LocalDateTime.now());
        inbound.setRemark(reason);
        this.updateById(inbound);
        log.info("入库单拒收: inboundNo={}, reason={}", inbound.getInboundNo(), reason);
        return inbound;
    }

    @Override
    public ReagentInbound getDetail(Long id) {
        ReagentInbound inbound = this.getById(id);
        if (inbound == null) {
            throw new BusinessException("入库单不存在: " + id);
        }
        List<ReagentInboundItem> items = inboundItemMapper.selectList(
                new LambdaQueryWrapper<ReagentInboundItem>().eq(ReagentInboundItem::getInboundId, id));
        for (ReagentInboundItem item : items) {
            Reagent reagent = reagentMapper.selectById(item.getReagentId());
            if (reagent != null) {
                item.setReagentName(reagent.getReagentName());
                item.setReagentCode(reagent.getReagentCode());
            }
        }
        inbound.setItems(items);
        return inbound;
    }

    @Override
    public Page<ReagentInbound> queryPage(long current, long size, String inboundNo, Integer verifyStatus) {
        LambdaQueryWrapper<ReagentInbound> wrapper = new LambdaQueryWrapper<>();
        if (inboundNo != null && !inboundNo.isEmpty()) {
            wrapper.like(ReagentInbound::getInboundNo, inboundNo);
        }
        if (verifyStatus != null) {
            wrapper.eq(ReagentInbound::getVerifyStatus, verifyStatus);
        }
        wrapper.orderByDesc(ReagentInbound::getCreateTime);
        return this.page(new Page<>(current, size), wrapper);
    }

    /**
     * 库内当日最大入库单号（供 BizNoGenerator 续号）。
     */
    private String selectMaxInboundNo(String head) {
        return baseMapper.selectMaxNo(head);
    }

    @Override
    public Map<String, Object> resolveScanCode(String code) {
        if (code == null || code.trim().isEmpty()) {
            throw new BusinessException("条码不能为空");
        }
        String barcode = code.trim();
        // 1. 优先按试剂条码别名匹配
        Reagent reagent = reagentMapper.selectOne(new LambdaQueryWrapper<Reagent>()
                .eq(Reagent::getBarcodeAlias, barcode)
                .last("LIMIT 1"));
        Long stockId = null;
        String batchNo = null;
        if (reagent == null) {
            // 2. 再按库存批次条码匹配
            ReagentStock stock = stockMapper.selectOne(new LambdaQueryWrapper<ReagentStock>()
                    .eq(ReagentStock::getBarcode, barcode)
                    .last("LIMIT 1"));
            if (stock == null) {
                throw new BusinessException("条码未匹配到试剂");
            }
            stockId = stock.getId();
            batchNo = stock.getLotNo();
            reagent = reagentMapper.selectById(stock.getReagentId());
        }
        if (reagent == null) {
            throw new BusinessException("条码未匹配到试剂");
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("reagentId", reagent.getId());
        result.put("reagentName", reagent.getReagentName());
        result.put("batchNo", batchNo);
        result.put("stockId", stockId);
        return result;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ReagentInbound createScanInbound(String inboundMode, List<ReagentInboundItem> items, String remark) {
        if (items == null || items.isEmpty()) {
            throw new BusinessException("入库明细不能为空");
        }
        ReagentInbound inbound = new ReagentInbound();
        inbound.setInboundMode(inboundMode);
        inbound.setRemark(remark);
        inbound.setItems(items);
        return createInbound(inbound);
    }
}
