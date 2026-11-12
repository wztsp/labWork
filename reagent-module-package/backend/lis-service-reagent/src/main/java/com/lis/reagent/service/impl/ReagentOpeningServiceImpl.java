package com.lis.reagent.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.lis.reagent.entity.Reagent;
import com.lis.reagent.entity.ReagentOpening;
import com.lis.reagent.mapper.ReagentMapper;
import com.lis.reagent.mapper.ReagentOpeningMapper;
import com.lis.reagent.service.ReagentOpeningService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class ReagentOpeningServiceImpl extends ServiceImpl<ReagentOpeningMapper, ReagentOpening>
        implements ReagentOpeningService {

    private final ReagentMapper reagentMapper;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ReagentOpening createOpening(ReagentOpening opening) {
        if (opening.getOpenTime() == null) {
            opening.setOpenTime(LocalDateTime.now());
        }
        Reagent reagent = reagentMapper.selectById(opening.getReagentId());
        if (reagent != null) {
            opening.setReagentName(reagent.getReagentName());
            if (reagent.getOpenedExpiryDays() != null) {
                opening.setOpenedExpiry(LocalDate.now().plusDays(reagent.getOpenedExpiryDays()));
            }
        }
        opening.setStatus(1);
        opening.setCreateTime(LocalDateTime.now());
        this.save(opening);
        log.info("试剂开瓶登记: reagentId={}, stockId={}, openedExpiry={}",
                opening.getReagentId(), opening.getStockId(), opening.getOpenedExpiry());
        return opening;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int lockExpiredOpenings() {
        List<ReagentOpening> expired = this.list(new LambdaQueryWrapper<ReagentOpening>()
                .eq(ReagentOpening::getStatus, 1)
                .le(ReagentOpening::getOpenedExpiry, LocalDate.now()));
        int count = 0;
        for (ReagentOpening o : expired) {
            o.setStatus(0);
            this.updateById(o);
            count++;
        }
        if (count > 0) {
            log.info("锁定开瓶过期记录 {} 个", count);
        }
        return count;
    }

    @Override
    public Page<ReagentOpening> queryPage(long current, long size, Long reagentId, Integer status) {
        LambdaQueryWrapper<ReagentOpening> wrapper = new LambdaQueryWrapper<>();
        if (reagentId != null) {
            wrapper.eq(ReagentOpening::getReagentId, reagentId);
        }
        if (status != null) {
            wrapper.eq(ReagentOpening::getStatus, status);
        }
        wrapper.orderByDesc(ReagentOpening::getOpenTime);
        Page<ReagentOpening> page = this.page(new Page<>(current, size), wrapper);
        for (ReagentOpening o : page.getRecords()) {
            Reagent reagent = reagentMapper.selectById(o.getReagentId());
            if (reagent != null) {
                o.setReagentName(reagent.getReagentName());
            }
        }
        return page;
    }
}
