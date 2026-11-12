package com.lis.micro.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.IService;
import com.lis.micro.entity.AntibioticBreakpoint;

import java.util.List;

public interface AntibioticBreakpointService extends IService<AntibioticBreakpoint> {

    Page<AntibioticBreakpoint> queryPage(long current, long size, Long antibioticId, Long bacteriaId,
                                         String standard, String method);

    /** 查询某抗生素+细菌的所有折点 */
    List<AntibioticBreakpoint> listByAntibioticAndBacteria(Long antibioticId, Long bacteriaId);

    /** 查询某细菌的所有折点（用于药敏录入时自动带出） */
    List<AntibioticBreakpoint> listByBacteria(Long bacteriaId);

    List<AntibioticBreakpoint> listAllEnabled();
}
