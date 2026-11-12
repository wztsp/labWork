package com.lis.reagent.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.lis.common.core.exception.BusinessException;
import com.lis.reagent.entity.Reagent;
import com.lis.reagent.entity.ReagentConsumption;
import com.lis.reagent.entity.ReagentStock;
import com.lis.reagent.entity.ReagentStockRecord;
import com.lis.reagent.mapper.ReagentConsumptionMapper;
import com.lis.reagent.mapper.ReagentMapper;
import com.lis.reagent.mapper.ReagentStockRecordMapper;
import com.lis.reagent.service.ReagentConsumptionService;
import com.lis.reagent.service.ReagentStockService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class ReagentConsumptionServiceImpl extends ServiceImpl<ReagentConsumptionMapper, ReagentConsumption>
        implements ReagentConsumptionService {

    private final ReagentStockService stockService;
    private final ReagentStockRecordMapper stockRecordMapper;
    private final ReagentMapper reagentMapper;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ReagentConsumption createConsumption(ReagentConsumption consumption) {
        consumption.setConsumptionNo("CON" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss")));
        if (consumption.getConsumeType() == null) {
            consumption.setConsumeType(1);
        }
        consumption.setCreateTime(LocalDateTime.now());

        Long reagentId = consumption.getReagentId();
        BigDecimal quantity = consumption.getQuantity();
        Long operatorId = consumption.getOperatorId();
        String operatorName = consumption.getOperatorName();
        String sourceNo = consumption.getConsumptionNo();

        Long stockId;
        if (consumption.getStockId() != null) {
            ReagentStockRecord rec = stockService.outStock(consumption.getStockId(), quantity,
                    operatorId, operatorName, sourceNo, "手工消耗");
            stockId = rec.getStockId();
        } else {
            List<ReagentStockRecord> records = stockService.outStockFifo(reagentId, quantity,
                    operatorId, operatorName, sourceNo, "手工消耗");
            stockId = records.isEmpty() ? null : records.get(0).getStockId();
        }
        consumption.setStockId(stockId);
        if (stockId != null) {
            ReagentStock stock = stockService.getById(stockId);
            if (stock != null) {
                consumption.setLotNo(stock.getLotNo());
            }
            writeConsumptionStockRecord(stockId, reagentId, quantity, operatorId, operatorName, sourceNo, "消耗");
        }
        this.save(consumption);
        log.info("试剂消耗登记: consumptionNo={}, reagentId={}, quantity={}",
                consumption.getConsumptionNo(), reagentId, quantity);
        return consumption;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void autoConsume(Long testItemId, Integer testCount, Long operatorId, String operatorName, String relatedNo) {
        List<Reagent> reagents = reagentMapper.selectList(new LambdaQueryWrapper<Reagent>()
                .eq(Reagent::getTestItemId, testItemId)
                .eq(Reagent::getStatus, 1));
        String baseNo = "CON" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss"));
        for (Reagent reagent : reagents) {
            try {
                if (reagent.getUsagePerTest() == null) {
                    continue;
                }
                BigDecimal quantity = reagent.getUsagePerTest().multiply(new BigDecimal(testCount));
                List<ReagentStockRecord> records;
                try {
                    records = stockService.outStockFifo(reagent.getId(), quantity,
                            operatorId, operatorName, relatedNo, "自动消耗");
                } catch (BusinessException e) {
                    log.warn("试剂自动扣减库存不足: reagentId={}, testItemId={}, quantity={}, msg={}",
                            reagent.getId(), testItemId, quantity, e.getMessage());
                    continue;
                }
                Long stockId = records.isEmpty() ? null : records.get(0).getStockId();

                ReagentConsumption consumption = new ReagentConsumption();
                consumption.setConsumptionNo(baseNo + reagent.getId());
                consumption.setReagentId(reagent.getId());
                consumption.setReagentName(reagent.getReagentName());
                consumption.setStockId(stockId);
                consumption.setQuantity(quantity);
                consumption.setUnit(reagent.getUnit());
                consumption.setConsumeType(2);
                consumption.setRelatedNo(relatedNo);
                consumption.setOperatorId(operatorId);
                consumption.setOperatorName(operatorName);
                consumption.setCreateTime(LocalDateTime.now());
                if (stockId != null) {
                    ReagentStock stock = stockService.getById(stockId);
                    if (stock != null) {
                        consumption.setLotNo(stock.getLotNo());
                    }
                }
                this.save(consumption);
                if (stockId != null) {
                    writeConsumptionStockRecord(stockId, reagent.getId(), quantity,
                            operatorId, operatorName, relatedNo, "自动消耗");
                }
                log.info("试剂自动消耗: reagentId={}, testItemId={}, quantity={}",
                        reagent.getId(), testItemId, quantity);
            } catch (Exception e) {
                log.error("试剂自动扣减异常: reagentId={}, testItemId={}", reagent.getId(), testItemId, e);
            }
        }
    }

    @Override
    public Page<ReagentConsumption> queryPage(long current, long size, Long reagentId, Integer consumeType, String relatedNo) {
        LambdaQueryWrapper<ReagentConsumption> wrapper = new LambdaQueryWrapper<>();
        if (reagentId != null) {
            wrapper.eq(ReagentConsumption::getReagentId, reagentId);
        }
        if (consumeType != null) {
            wrapper.eq(ReagentConsumption::getConsumeType, consumeType);
        }
        if (relatedNo != null && !relatedNo.isEmpty()) {
            wrapper.like(ReagentConsumption::getRelatedNo, relatedNo);
        }
        wrapper.orderByDesc(ReagentConsumption::getCreateTime);
        Page<ReagentConsumption> page = this.page(new Page<>(current, size), wrapper);
        for (ReagentConsumption c : page.getRecords()) {
            Reagent reagent = reagentMapper.selectById(c.getReagentId());
            if (reagent != null) {
                c.setReagentName(reagent.getReagentName());
            }
        }
        return page;
    }

    private void writeConsumptionStockRecord(Long stockId, Long reagentId, BigDecimal quantity,
                                              Long operatorId, String operatorName, String sourceNo, String remark) {
        ReagentStockRecord record = new ReagentStockRecord();
        record.setStockId(stockId);
        record.setReagentId(reagentId);
        record.setRecordType(6);
        record.setQuantity(quantity.negate());
        record.setOperatorId(operatorId);
        record.setOperatorName(operatorName);
        record.setSourceNo(sourceNo);
        record.setRemark(remark);
        record.setCreateTime(LocalDateTime.now());
        stockRecordMapper.insert(record);
    }
}
