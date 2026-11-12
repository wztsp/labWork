package com.lis.reagent.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.lis.common.core.exception.BusinessException;
import com.lis.reagent.entity.ReagentScrapDisposal;
import com.lis.reagent.mapper.ReagentScrapDisposalMapper;
import com.lis.reagent.service.ReagentScrapDisposalService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

/**
 * 报废单环保处置信息服务实现（T-15）。
 *
 * <p>按 {@code reqId} 幂等 upsert：已存在则更新，否则插入。</p>
 *
 * @author LIS Team
 */
@Service
@RequiredArgsConstructor
public class ReagentScrapDisposalServiceImpl implements ReagentScrapDisposalService {

    private final ReagentScrapDisposalMapper scrapDisposalMapper;

    @Override
    public ReagentScrapDisposal getByReqId(Long reqId) {
        if (reqId == null) {
            return null;
        }
        return scrapDisposalMapper.selectOne(new LambdaQueryWrapper<ReagentScrapDisposal>()
                .eq(ReagentScrapDisposal::getReqId, reqId));
    }

    @Override
    public ReagentScrapDisposal save(ReagentScrapDisposal dto) {
        if (dto == null || dto.getReqId() == null) {
            throw new BusinessException("报废单 ID（reqId）不能为空");
        }
        // 危险品报废必须补齐处置四要素
        if (dto.getHazardFlag() != null && dto.getHazardFlag() == 1
                && (blank(dto.getDisposalMethod()) || blank(dto.getDisposalOrg())
                || blank(dto.getDisposalCert()) || dto.getDisposalDate() == null)) {
            throw new BusinessException("危险品报废必须填写处置方式/单位/凭证/日期");
        }
        ReagentScrapDisposal existing = getByReqId(dto.getReqId());
        LocalDateTime now = LocalDateTime.now();
        if (existing == null) {
            dto.setCreateTime(now);
            dto.setUpdateTime(now);
            scrapDisposalMapper.insert(dto);
            return dto;
        }
        dto.setId(existing.getId());
        dto.setCreateTime(existing.getCreateTime());
        dto.setUpdateTime(now);
        scrapDisposalMapper.updateById(dto);
        return dto;
    }

    @Override
    public Page<ReagentScrapDisposal> page(long current, long size) {
        return scrapDisposalMapper.selectPage(new Page<>(current, size),
                new LambdaQueryWrapper<ReagentScrapDisposal>()
                        .orderByDesc(ReagentScrapDisposal::getCreateTime));
    }

    @Override
    public void deleteById(Long id) {
        if (id == null) {
            throw new BusinessException("处置记录 ID 不能为空");
        }
        ReagentScrapDisposal existing = scrapDisposalMapper.selectById(id);
        if (existing == null) {
            throw new BusinessException("处置记录不存在: " + id);
        }
        scrapDisposalMapper.deleteById(id);
    }

    private static boolean blank(String s) {
        return s == null || s.trim().isEmpty();
    }
}