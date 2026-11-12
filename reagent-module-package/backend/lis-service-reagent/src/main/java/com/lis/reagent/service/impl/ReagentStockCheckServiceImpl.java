package com.lis.reagent.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.lis.common.core.exception.BusinessException;
import com.lis.reagent.dto.ItemSubmitDTO;
import com.lis.reagent.dto.StockCheckCreateDTO;
import com.lis.reagent.dto.StockOpContext;
import com.lis.reagent.entity.ReagentStock;
import com.lis.reagent.entity.ReagentStockCheck;
import com.lis.reagent.entity.ReagentStockCheckItem;
import com.lis.reagent.enums.CheckStatus;
import com.lis.reagent.enums.RecordType;
import com.lis.reagent.mapper.ReagentStockCheckItemMapper;
import com.lis.reagent.mapper.ReagentStockCheckMapper;
import com.lis.reagent.mapper.ReagentStockMapper;
import com.lis.reagent.service.ReagentStockCheckService;
import com.lis.reagent.service.ReagentStockService;
import com.lis.reagent.service.ReagentWarehouseService;
import com.lis.reagent.support.StockCheckCalculator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 库存盘点服务实现（R-07 + 缺陷 P 修复）。
 *
 * @author LIS Team
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ReagentStockCheckServiceImpl
        extends ServiceImpl<ReagentStockCheckMapper, ReagentStockCheck>
        implements ReagentStockCheckService {

    private final ReagentStockCheckMapper checkMapper;
    private final ReagentStockCheckItemMapper checkItemMapper;
    private final ReagentStockMapper stockMapper;
    private final ReagentStockService stockService;
    private final ReagentWarehouseService warehouseService;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ReagentStockCheck create(StockCheckCreateDTO dto) {
        if (dto == null) {
            throw new BusinessException("创建参数不能为空");
        }
        ReagentStockCheck check = new ReagentStockCheck();
        check.setCheckNo("SC" + System.currentTimeMillis());
        check.setCheckDate(dto.getCheckDate() != null ? dto.getCheckDate() : LocalDate.now());
        check.setStatus(CheckStatus.IN_PROGRESS.getCode());
        check.setOperatorId(dto.getOperatorId());
        check.setOperatorName(dto.getOperatorName());
        check.setRemark(dto.getRemark());
        check.setCreateTime(LocalDateTime.now());
        checkMapper.insert(check);

        // 按仓库（含子树）生成账面明细；warehouseId 为空时盘点全库
        List<Long> whIds;
        if (dto.getWarehouseId() == null) {
            whIds = null; // 全库
        } else if (Boolean.TRUE.equals(dto.getIncludeChildren())) {
            whIds = warehouseService.subtreeIds(dto.getWarehouseId());
        } else {
            whIds = java.util.Collections.singletonList(dto.getWarehouseId());
        }
        LambdaQueryWrapper<ReagentStock> wrapper = new LambdaQueryWrapper<>();
        if (whIds != null) {
            wrapper.in(ReagentStock::getWarehouseId, whIds);
        }
        List<ReagentStock> stocks = stockMapper.selectList(wrapper);
        for (ReagentStock s : stocks) {
            ReagentStockCheckItem item = new ReagentStockCheckItem();
            item.setCheckId(check.getId());
            item.setStockId(s.getId());
            item.setReagentId(s.getReagentId());
            item.setLotNo(s.getLotNo());
            item.setBookQty(s.getQuantity());
            item.setActualQty(s.getQuantity());
            item.setDiffQty(BigDecimal.ZERO);
            item.setCreateTime(LocalDateTime.now());
            checkItemMapper.insert(item);
        }
        log.info("创建盘点单: checkNo={}, 明细 {} 条", check.getCheckNo(), stocks.size());
        return check;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void submit(Long checkId, List<ItemSubmitDTO> items, StockOpContext ctx) {
        if (items == null || items.isEmpty()) {
            throw new BusinessException("盘点明细不能为空");
        }
        // 1. 悲观锁加载盘点单，防重复提交（缺陷 P，R-07 验收 5）
        ReagentStockCheck check = checkMapper.selectByIdForUpdate(checkId);
        if (check == null) {
            throw new BusinessException("盘点单不存在: " + checkId);
        }
        CheckStatus status = CheckStatus.of(check.getStatus());
        if (status != CheckStatus.IN_PROGRESS) {
            throw new BusinessException("盘点单当前状态["
                    + (status == null ? check.getStatus() : status.getLabel())
                    + "]不允许提交，仅「" + CheckStatus.IN_PROGRESS.getLabel() + "」可提交");
        }

        // 2. 逐条：差异计算走纯 POJO → 写明细 → 差异非 0 时调账（同事务）
        for (ItemSubmitDTO dto : items) {
            if (dto == null || dto.getItemId() == null) {
                continue;
            }
            ReagentStockCheckItem item = checkItemMapper.selectById(dto.getItemId());
            if (item == null || !checkId.equals(item.getCheckId())) {
                throw new BusinessException("盘点明细不属于本单: itemId=" + dto.getItemId());
            }
            BigDecimal diff = StockCheckCalculator.diff(dto.getActualQty(), item.getBookQty());
            item.setActualQty(dto.getActualQty());
            item.setDiffQty(diff);
            item.setRemark(dto.getRemark());
            checkItemMapper.updateById(item);

            // 3. 差异为 0 不写流水（R-07 验收 4，避免流水表噪音）
            if (StockCheckCalculator.needAdjust(diff)) {
                stockService.adjustTo(item.getStockId(), dto.getActualQty(),
                        RecordType.CHECK_ADJUST.getCode(),
                        ctx.withSource(check.getCheckNo(), "CHECK", checkId));
            }
        }

        // 4. 标记已完成（终态，后续重复提交被 1 拦下）
        check.setStatus(CheckStatus.FINISHED.getCode());
        checkMapper.updateById(check);
        log.info("盘点单提交完成: checkId={}, itemCount={}", checkId, items.size());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void voidCheck(Long checkId, String operatorName, String reason) {
        ReagentStockCheck check = checkMapper.selectByIdForUpdate(checkId);
        if (check == null) {
            throw new BusinessException("盘点单不存在: " + checkId);
        }
        CheckStatus status = CheckStatus.of(check.getStatus());
        if (status != CheckStatus.IN_PROGRESS) {
            throw new BusinessException("仅进行中的盘点单可作废: "
                    + (status == null ? check.getStatus() : status.getLabel()));
        }
        check.setStatus(CheckStatus.VOIDED.getCode());
        check.setRemark(reason != null ? reason : check.getRemark());
        checkMapper.updateById(check);
        log.info("盘点单作废: checkId={}, operator={}", checkId, operatorName);
    }
}
