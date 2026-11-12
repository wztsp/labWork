package com.lis.reagent.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.lis.common.core.exception.BusinessException;
import com.lis.reagent.dto.StockOpContext;
import com.lis.reagent.entity.Reagent;
import com.lis.reagent.entity.ReagentStock;
import com.lis.reagent.entity.ReagentStockRecord;
import com.lis.reagent.enums.RecordType;
import com.lis.reagent.enums.StockStatus;
import com.lis.reagent.mapper.ReagentMapper;
import com.lis.reagent.mapper.ReagentStockMapper;
import com.lis.reagent.mapper.ReagentStockRecordMapper;
import com.lis.reagent.service.ReagentStockService;
import com.lis.reagent.support.ReagentWarehouseGuard;
import com.lis.reagent.vo.LowStockAlertDTO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class ReagentStockServiceImpl extends ServiceImpl<ReagentStockMapper, ReagentStock>
        implements ReagentStockService {

    private final ReagentStockRecordMapper recordMapper;
    private final ReagentMapper reagentMapper;

    @Override
    public Page<ReagentStock> queryPage(long current, long size, String reagentName, String batchNo,
                                         String barcode, Integer status, String location) {
        Set<Long> reagentIds = null;
        if (reagentName != null && !reagentName.trim().isEmpty()) {
            List<Reagent> matches = reagentMapper.selectList(new LambdaQueryWrapper<Reagent>()
                    .like(Reagent::getReagentName, reagentName.trim()));
            if (matches.isEmpty()) {
                return new Page<>(current, size, 0);
            }
            reagentIds = matches.stream().map(Reagent::getId).collect(Collectors.toSet());
        }
        LambdaQueryWrapper<ReagentStock> wrapper = new LambdaQueryWrapper<>();
        if (reagentIds != null) {
            wrapper.in(ReagentStock::getReagentId, reagentIds);
        }
        if (batchNo != null && !batchNo.trim().isEmpty()) {
            wrapper.like(ReagentStock::getLotNo, batchNo.trim());
        }
        if (barcode != null && !barcode.trim().isEmpty()) {
            String b = barcode.trim();
            wrapper.and(w -> w.eq(ReagentStock::getBarcode, b).or().likeRight(ReagentStock::getBarcode, b));
        }
        if (status != null) {
            wrapper.eq(ReagentStock::getStatus, status);
        }
        if (location != null && !location.isEmpty()) {
            wrapper.like(ReagentStock::getLocation, location);
        }
        wrapper.orderByAsc(ReagentStock::getExpiryDate);
        Page<ReagentStock> page = this.page(new Page<>(current, size), wrapper);
        fillReagentNames(page.getRecords());
        return page;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ReagentStock inStock(ReagentStock stock, BigDecimal quantity, Long operatorId,
                                String operatorName, String sourceNo, String remark) {
        StockOpContext ctx = StockOpContext.builder()
                .operatorId(operatorId)
                .operatorName(operatorName)
                .sourceNo(sourceNo)
                .remark(remark)
                .build();
        return inStockTyped(stock, quantity, RecordType.IN.getCode(), ctx);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ReagentStock inStockTyped(ReagentStock stock, BigDecimal quantity, int recordType, StockOpContext ctx) {
        if (quantity == null || quantity.compareTo(BigDecimal.ZERO) <= 0) {
            throw new BusinessException("入库数量必须大于0");
        }
        if (stock.getReagentId() == null) {
            throw new BusinessException("入库缺少试剂ID");
        }
        // 仓库 ID 归一化：null → 0（默认库房）。绝不让 null 进入 LambdaQueryWrapper（缺陷 K）
        long wh = ReagentWarehouseGuard.normalize(stock.getWarehouseId());
        stock.setWarehouseId(wh);
        // 合批键：reagentId + lotNo + warehouseId（缺陷 K 核心，三列唯一索引 uk_lis_reagent_stock_lot_wh 兜底）
        ReagentStock existing = this.getOne(new LambdaQueryWrapper<ReagentStock>()
                .eq(ReagentStock::getReagentId, stock.getReagentId())
                .eq(ReagentStock::getLotNo, stock.getLotNo())
                .eq(ReagentStock::getWarehouseId, wh));
        BigDecimal beforeQty;
        if (existing != null) {
            beforeQty = existing.getQuantity();
            existing.setQuantity(existing.getQuantity().add(quantity));
            existing.setStatus(StockStatus.AVAILABLE.getCode());
            // 合批时同步非空条码：旧批次无条码但本次新入库有 → 补齐；两边都有但不一致 → 保留原值打印WARN避免误覆盖
            String oldBar = existing.getBarcode();
            String newBar = stock.getBarcode();
            if (newBar != null && !newBar.trim().isEmpty()) {
                String trimmedNew = newBar.trim();
                if (oldBar == null || oldBar.trim().isEmpty()) {
                    existing.setBarcode(trimmedNew);
                } else if (!oldBar.trim().equals(trimmedNew)) {
                    log.warn("库存合批条码不一致: reagentId={}, lot={}, wh={}, existingBar={}, incomingBar={}, 保留原条码",
                            stock.getReagentId(), stock.getLotNo(), wh, oldBar, trimmedNew);
                }
            }
            // 合并补充库位/到期日（优先保留原值，空则补齐）
            if ((existing.getLocation() == null || existing.getLocation().trim().isEmpty())
                    && stock.getLocation() != null && !stock.getLocation().trim().isEmpty()) {
                existing.setLocation(stock.getLocation().trim());
            }
            if (existing.getExpiryDate() == null && stock.getExpiryDate() != null) {
                existing.setExpiryDate(stock.getExpiryDate());
            }
            this.updateById(existing);
            stock = existing;
        } else {
            stock.setQuantity(quantity);
            stock.setStatus(StockStatus.AVAILABLE.getCode());
            stock.setInStockTime(LocalDateTime.now());
            stock.setCreateTime(LocalDateTime.now());
            if (stock.getLocation() != null) stock.setLocation(stock.getLocation().trim());
            if (stock.getBarcode() != null) stock.setBarcode(stock.getBarcode().trim());
            this.save(stock);
            beforeQty = BigDecimal.ZERO;
        }
        writeRecord(stock, recordType, quantity, beforeQty, stock.getQuantity(), ctx);
        log.info("试剂入库: reagentId={}, lot={}, wh={}, qty={}, after={}",
                stock.getReagentId(), stock.getLotNo(), wh, quantity, stock.getQuantity());
        return stock;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public List<ReagentStockRecord> outStockFefo(Long reagentId, BigDecimal quantity, Long operatorId,
                                                 String operatorName, String sourceNo, String remark) {
        if (quantity == null || quantity.compareTo(BigDecimal.ZERO) <= 0) {
            throw new BusinessException("出库数量必须大于0");
        }
        StockOpContext ctx = StockOpContext.builder()
                .operatorId(operatorId)
                .operatorName(operatorName)
                .sourceNo(sourceNo)
                .remark(remark)
                .build();
        // FEFO：取状态可用且未过期、按到期日升序的批次
        List<ReagentStock> batches = this.list(new LambdaQueryWrapper<ReagentStock>()
                .eq(ReagentStock::getReagentId, reagentId)
                .eq(ReagentStock::getStatus, StockStatus.AVAILABLE.getCode())
                .gt(ReagentStock::getExpiryDate, LocalDate.now())
                .orderByAsc(ReagentStock::getExpiryDate));
        BigDecimal remaining = quantity;
        List<ReagentStockRecord> records = new ArrayList<>();
        for (ReagentStock batch : batches) {
            if (remaining.compareTo(BigDecimal.ZERO) <= 0) break;
            BigDecimal take = batch.getQuantity().min(remaining);
            ReagentStockRecord rec = doOutStock(batch, take, RecordType.OUT_PICK.getCode(), ctx);
            records.add(rec);
            remaining = remaining.subtract(take);
        }
        if (remaining.compareTo(BigDecimal.ZERO) > 0) {
            throw new BusinessException("试剂库存不足, 缺口: " + remaining);
        }
        return records;
    }

    @Override
    @Deprecated(since = "1.1")
    public List<ReagentStockRecord> outStockFifo(Long reagentId, BigDecimal quantity, Long operatorId,
                                                 String operatorName, String sourceNo, String remark) {
        return outStockFefo(reagentId, quantity, operatorId, operatorName, sourceNo, remark);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ReagentStockRecord outStock(Long stockId, BigDecimal quantity, Long operatorId,
                                       String operatorName, String sourceNo, String remark) {
        StockOpContext ctx = StockOpContext.builder()
                .operatorId(operatorId)
                .operatorName(operatorName)
                .sourceNo(sourceNo)
                .remark(remark)
                .build();
        return outStockTyped(stockId, quantity, RecordType.OUT_PICK.getCode(), ctx);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ReagentStockRecord outStockTyped(Long stockId, BigDecimal quantity, int recordType, StockOpContext ctx) {
        ReagentStock stock = this.getById(stockId);
        if (stock == null) {
            throw new BusinessException("库存批次不存在: " + stockId);
        }
        if (stock.getStatus() != StockStatus.AVAILABLE.getCode()) {
            throw new BusinessException("该批次已被锁定/耗尽, 不允许出库");
        }
        if (quantity == null || quantity.compareTo(BigDecimal.ZERO) <= 0) {
            throw new BusinessException("出库数量必须大于0");
        }
        if (stock.getQuantity().compareTo(quantity) < 0) {
            throw new BusinessException("批次库存不足, 当前: " + stock.getQuantity() + ", 需要: " + quantity);
        }
        return doOutStock(stock, quantity, recordType, ctx);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ReagentStockRecord adjustTo(Long stockId, BigDecimal actualQty, int recordType, StockOpContext ctx) {
        if (actualQty == null) {
            throw new BusinessException("实盘数量不能为空");
        }
        if (actualQty.compareTo(BigDecimal.ZERO) < 0) {
            throw new BusinessException("实盘数量不能为负数");
        }
        ReagentStock stock = this.getById(stockId);
        if (stock == null) {
            throw new BusinessException("库存批次不存在: " + stockId);
        }
        BigDecimal beforeQty = stock.getQuantity();
        BigDecimal diff = actualQty.subtract(beforeQty);
        // 差异为 0 不写流水（R-07 验收 4，避免流水表噪音）
        if (diff.compareTo(BigDecimal.ZERO) == 0) {
            return null;
        }
        stock.setQuantity(actualQty);
        if (actualQty.compareTo(BigDecimal.ZERO) == 0) {
            stock.setStatus(StockStatus.EXHAUSTED.getCode()); // 实盘归零置已耗尽
        } else {
            stock.setStatus(StockStatus.AVAILABLE.getCode());
        }
        this.updateById(stock);
        ReagentStockRecord record = writeRecord(stock, recordType, diff, beforeQty, actualQty, ctx);
        log.info("盘点调账: stockId={}, diff={}, before={}, after={}",
                stockId, diff, beforeQty, actualQty);
        return record;
    }

    @Override
    public List<LowStockAlertDTO> listLowStock() {
        return reagentMapper.selectLowStock(null);
    }

    @Override
    @Deprecated(since = "1.1")
    public List<ReagentStock> listBelowSafetyStock() {
        // 查询所有试剂的最低库存(minStock), 与当前可用库存汇总对比
        // 注: 原读 safetyStock 字段已废弃(@Deprecated), 统一收敛到 minStock, 二者语义等价
        List<Reagent> reagents = reagentMapper.selectList(new LambdaQueryWrapper<Reagent>()
                .eq(Reagent::getStatus, 1)
                .isNotNull(Reagent::getMinStock));
        List<ReagentStock> alerts = new ArrayList<>();
        for (Reagent r : reagents) {
            List<ReagentStock> stocks = this.list(new LambdaQueryWrapper<ReagentStock>()
                    .eq(ReagentStock::getReagentId, r.getId())
                    .eq(ReagentStock::getStatus, StockStatus.AVAILABLE.getCode()));
            BigDecimal total = stocks.stream().map(ReagentStock::getQuantity)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            if (r.getMinStock() != null && total.compareTo(r.getMinStock()) < 0) {
                ReagentStock alert = new ReagentStock();
                alert.setReagentId(r.getId());
                alert.setReagentName(r.getReagentName());
                alert.setReagentCode(r.getReagentCode());
                alert.setQuantity(total);
                alert.setLocation("安全库存: " + r.getMinStock());
                alerts.add(alert);
            }
        }
        return alerts;
    }

    @Override
    public List<ReagentStock> listNearExpiry(int days) {
        LocalDate threshold = LocalDate.now().plusDays(days);
        List<ReagentStock> list = this.list(new LambdaQueryWrapper<ReagentStock>()
                .eq(ReagentStock::getStatus, StockStatus.AVAILABLE.getCode())
                .le(ReagentStock::getExpiryDate, threshold)
                .gt(ReagentStock::getExpiryDate, LocalDate.now())
                .orderByAsc(ReagentStock::getExpiryDate));
        fillReagentNames(list);
        return list;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int lockExpiredStock() {
        List<ReagentStock> expired = this.list(new LambdaQueryWrapper<ReagentStock>()
                .eq(ReagentStock::getStatus, StockStatus.AVAILABLE.getCode())
                .le(ReagentStock::getExpiryDate, LocalDate.now()));
        int count = 0;
        for (ReagentStock s : expired) {
            s.setStatus(StockStatus.EXPIRED_LOCKED.getCode()); // 过期锁定
            this.updateById(s);
            count++;
        }
        if (count > 0) {
            log.info("锁定过期试剂批次 {} 个", count);
        }
        return count;
    }

    /**
     * 批量带出试剂名称/编码（批量查 lis_reagent，null 安全：试剂被删/不存在时字段为 null，前端已有兜底）。
     */
    private void fillReagentNames(List<ReagentStock> stocks) {
        if (stocks == null || stocks.isEmpty()) {
            return;
        }
        Set<Long> reagentIds = stocks.stream()
                .map(ReagentStock::getReagentId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        if (reagentIds.isEmpty()) {
            return;
        }
        Map<Long, Reagent> reagentMap = reagentMapper.selectBatchIds(reagentIds).stream()
                .collect(Collectors.toMap(Reagent::getId, Function.identity(), (a, b) -> a));
        for (ReagentStock stock : stocks) {
            Reagent reagent = reagentMap.get(stock.getReagentId());
            if (reagent != null) {
                stock.setReagentName(reagent.getReagentName());
                stock.setReagentCode(reagent.getReagentCode());
            }
        }
    }

    /**
     * 指定批次出库的核心实现：扣减数量、归零置耗尽、写流水。
     */
    private ReagentStockRecord doOutStock(ReagentStock stock, BigDecimal quantity, int recordType, StockOpContext ctx) {
        BigDecimal beforeQty = stock.getQuantity();
        stock.setQuantity(beforeQty.subtract(quantity));
        if (stock.getQuantity().compareTo(BigDecimal.ZERO) == 0) {
            stock.setStatus(StockStatus.EXHAUSTED.getCode()); // 已耗尽
        }
        this.updateById(stock);
        return writeRecord(stock, recordType, quantity.negate(), beforeQty, stock.getQuantity(), ctx);
    }

    /**
     * 统一流水写入点：所有库存变动（入库/出库/调账）都必须经过这里，
     * 同步写入业务上下文（操作人 / 科室 / 来源单据），保证「账实相符 + 可追溯」。
     */
    private ReagentStockRecord writeRecord(ReagentStock stock, int recordType, BigDecimal quantity,
                                           BigDecimal beforeQty, BigDecimal afterQty, StockOpContext ctx) {
        ReagentStockRecord record = new ReagentStockRecord();
        record.setStockId(stock.getId());
        record.setReagentId(stock.getReagentId());
        record.setRecordType(recordType);
        record.setQuantity(quantity);
        record.setBeforeQty(beforeQty);
        record.setAfterQty(afterQty);
        if (ctx != null) {
            record.setOperatorId(ctx.getOperatorId());
            record.setOperatorName(ctx.getOperatorName());
            record.setDeptId(ctx.getDeptId());
            record.setDeptName(ctx.getDeptName());
            record.setSourceNo(ctx.getSourceNo());
            record.setRelatedId(ctx.getRelatedId());
            record.setRelatedType(ctx.getRelatedType());
            record.setRemark(ctx.getRemark());
        }
        record.setCreateTime(LocalDateTime.now());
        recordMapper.insert(record);
        return record;
    }
}
