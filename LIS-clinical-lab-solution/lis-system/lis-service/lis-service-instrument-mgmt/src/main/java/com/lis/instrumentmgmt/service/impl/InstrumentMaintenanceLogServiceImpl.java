package com.lis.instrumentmgmt.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.lis.instrumentmgmt.entity.InstrumentMaintenanceLog;
import com.lis.instrumentmgmt.entity.InstrumentMaintenanceLogItem;
import com.lis.instrumentmgmt.mapper.InstrumentMaintenanceLogItemMapper;
import com.lis.instrumentmgmt.mapper.InstrumentMaintenanceLogMapper;
import com.lis.instrumentmgmt.service.InstrumentMaintenanceLogService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class InstrumentMaintenanceLogServiceImpl extends ServiceImpl<InstrumentMaintenanceLogMapper, InstrumentMaintenanceLog>
        implements InstrumentMaintenanceLogService {

    private final InstrumentMaintenanceLogItemMapper itemMapper;

    @Override
    public Page<InstrumentMaintenanceLog> queryPage(long current, long size, Long instrumentId,
                                                   String maintenanceType, LocalDate startDate, LocalDate endDate) {
        LambdaQueryWrapper<InstrumentMaintenanceLog> wrapper = new LambdaQueryWrapper<>();
        if (instrumentId != null) {
            wrapper.eq(InstrumentMaintenanceLog::getInstrumentId, instrumentId);
        }
        if (maintenanceType != null && !maintenanceType.isEmpty()) {
            wrapper.eq(InstrumentMaintenanceLog::getMaintenanceType, maintenanceType);
        }
        if (startDate != null) {
            wrapper.ge(InstrumentMaintenanceLog::getMaintenanceDate, startDate);
        }
        if (endDate != null) {
            wrapper.le(InstrumentMaintenanceLog::getMaintenanceDate, endDate);
        }
        wrapper.orderByDesc(InstrumentMaintenanceLog::getMaintenanceDate);
        return this.page(new Page<>(current, size), wrapper);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public InstrumentMaintenanceLog saveWithItems(InstrumentMaintenanceLog log, List<InstrumentMaintenanceLogItem> items) {
        if (log.getCreateTime() == null) {
            log.setCreateTime(LocalDateTime.now());
        }
        if (log.getResult() == null) {
            log.setResult(1);
        }
        this.save(log);
        if (items != null && !items.isEmpty()) {
            items.forEach(item -> {
                item.setId(null);
                item.setLogId(log.getId());
                itemMapper.insert(item);
            });
        }
        return log;
    }

    @Override
    public List<InstrumentMaintenanceLogItem> listItems(Long logId) {
        return itemMapper.selectList(new LambdaQueryWrapper<InstrumentMaintenanceLogItem>()
                .eq(InstrumentMaintenanceLogItem::getLogId, logId));
    }
}
