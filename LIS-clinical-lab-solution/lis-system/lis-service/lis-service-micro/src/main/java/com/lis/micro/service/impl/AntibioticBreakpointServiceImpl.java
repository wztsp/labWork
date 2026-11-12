package com.lis.micro.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.lis.micro.entity.AntibioticBreakpoint;
import com.lis.micro.mapper.AntibioticBreakpointMapper;
import com.lis.micro.service.AntibioticBreakpointService;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class AntibioticBreakpointServiceImpl
        extends ServiceImpl<AntibioticBreakpointMapper, AntibioticBreakpoint>
        implements AntibioticBreakpointService {

    @Override
    public Page<AntibioticBreakpoint> queryPage(long current, long size, Long antibioticId, Long bacteriaId,
                                                String standard, String method) {
        LambdaQueryWrapper<AntibioticBreakpoint> wrapper = new LambdaQueryWrapper<>();
        if (antibioticId != null) {
            wrapper.eq(AntibioticBreakpoint::getAntibioticId, antibioticId);
        }
        if (bacteriaId != null) {
            wrapper.eq(AntibioticBreakpoint::getBacteriaId, bacteriaId);
        }
        if (standard != null && !standard.isEmpty()) {
            wrapper.eq(AntibioticBreakpoint::getStandard, standard);
        }
        if (method != null && !method.isEmpty()) {
            wrapper.eq(AntibioticBreakpoint::getMethod, method);
        }
        wrapper.orderByAsc(AntibioticBreakpoint::getBacteriaId,
                AntibioticBreakpoint::getAntibioticId,
                AntibioticBreakpoint::getStandardVersion);
        return this.page(new Page<>(current, size), wrapper);
    }

    @Override
    public List<AntibioticBreakpoint> listByAntibioticAndBacteria(Long antibioticId, Long bacteriaId) {
        return this.list(new LambdaQueryWrapper<AntibioticBreakpoint>()
                .eq(AntibioticBreakpoint::getAntibioticId, antibioticId)
                .eq(AntibioticBreakpoint::getBacteriaId, bacteriaId)
                .eq(AntibioticBreakpoint::getStatus, 1));
    }

    @Override
    public List<AntibioticBreakpoint> listByBacteria(Long bacteriaId) {
        return this.list(new LambdaQueryWrapper<AntibioticBreakpoint>()
                .eq(AntibioticBreakpoint::getBacteriaId, bacteriaId)
                .eq(AntibioticBreakpoint::getStatus, 1));
    }

    @Override
    public List<AntibioticBreakpoint> listAllEnabled() {
        return this.list(new LambdaQueryWrapper<AntibioticBreakpoint>()
                .eq(AntibioticBreakpoint::getStatus, 1));
    }
}
