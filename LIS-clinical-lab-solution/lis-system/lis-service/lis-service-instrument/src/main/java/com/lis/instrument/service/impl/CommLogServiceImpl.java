package com.lis.instrument.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.lis.common.core.result.PageResult;
import com.lis.instrument.entity.InstrumentCommLog;
import com.lis.instrument.mapper.InstrumentCommLogMapper;
import com.lis.instrument.service.CommLogService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

/**
 * 仪器通讯日志服务实现
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CommLogServiceImpl implements CommLogService {

    private final InstrumentCommLogMapper instrumentCommLogMapper;

    @Override
    public void saveLog(InstrumentCommLog commLog) {
        if (commLog == null) return;
        if (commLog.getCreateTime() == null) {
            commLog.setCreateTime(LocalDateTime.now());
        }
        if (commLog.getMessageTime() == null) {
            commLog.setMessageTime(LocalDateTime.now());
        }
        try {
            instrumentCommLogMapper.insert(commLog);
        } catch (Exception e) {
            // 通讯日志保存失败不影响主流程
            log.error("保存仪器通讯日志失败: instrumentId={}", commLog.getInstrumentId(), e);
        }
    }

    @Override
    public PageResult<InstrumentCommLog> queryPage(Long instrumentId, int current, int size) {
        LambdaQueryWrapper<InstrumentCommLog> wrapper = new LambdaQueryWrapper<>();
        if (instrumentId != null) {
            wrapper.eq(InstrumentCommLog::getInstrumentId, instrumentId);
        }
        wrapper.orderByDesc(InstrumentCommLog::getCreateTime);
        Page<InstrumentCommLog> page = instrumentCommLogMapper.selectPage(
                new Page<>(current, size), wrapper);
        return new PageResult<>(page.getRecords(), page.getTotal(), page.getCurrent(), page.getSize());
    }
}
