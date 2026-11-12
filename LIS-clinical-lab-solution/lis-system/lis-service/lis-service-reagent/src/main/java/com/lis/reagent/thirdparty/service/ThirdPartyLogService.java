package com.lis.reagent.thirdparty.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.lis.reagent.thirdparty.entity.ThirdPartyLog;
import com.lis.reagent.mapper.ThirdPartyLogMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * 三方卫材调用日志查询服务。
 */
@Service
@RequiredArgsConstructor
public class ThirdPartyLogService {

    private final ThirdPartyLogMapper logMapper;

    public Page<ThirdPartyLog> queryPage(long current, long size, String partnerCode, String bizType,
                                         Integer direction, Integer success, String keyword) {
        LambdaQueryWrapper<ThirdPartyLog> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(partnerCode != null && !partnerCode.isEmpty(), ThirdPartyLog::getPartnerCode, partnerCode)
                .eq(bizType != null && !bizType.isEmpty(), ThirdPartyLog::getBizType, bizType)
                .eq(direction != null, ThirdPartyLog::getDirection, direction)
                .eq(success != null, ThirdPartyLog::getSuccess, success);
        if (keyword != null && !keyword.trim().isEmpty()) {
            wrapper.and(w -> w.like(ThirdPartyLog::getBizNo, keyword)
                    .or().like(ThirdPartyLog::getErrorMsg, keyword));
        }
        wrapper.orderByDesc(ThirdPartyLog::getId);
        return logMapper.selectPage(new Page<>(current, size), wrapper);
    }
}
