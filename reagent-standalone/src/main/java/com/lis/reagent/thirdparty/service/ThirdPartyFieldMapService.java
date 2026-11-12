package com.lis.reagent.thirdparty.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.lis.common.core.exception.BusinessException;
import com.lis.reagent.thirdparty.entity.ThirdPartyFieldMap;
import com.lis.reagent.mapper.ThirdPartyFieldMapMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 三方卫材字段映射服务。调整映射即可适配对方不同的字段标准，无需改代码。
 */
@Service
@RequiredArgsConstructor
public class ThirdPartyFieldMapService {

    private final ThirdPartyFieldMapMapper fieldMapMapper;

    public List<ThirdPartyFieldMap> list(String partnerCode, String bizType, Integer direction) {
        return fieldMapMapper.selectList(new LambdaQueryWrapper<ThirdPartyFieldMap>()
                .eq(ThirdPartyFieldMap::getPartnerCode, partnerCode)
                .eq(bizType != null, ThirdPartyFieldMap::getBizType, bizType)
                .eq(direction != null, ThirdPartyFieldMap::getDirection, direction)
                .orderByAsc(ThirdPartyFieldMap::getId));
    }

    /** 按「对接方+业务类型+方向」整组覆盖保存。 */
    @Transactional(rollbackFor = Exception.class)
    public void replace(String partnerCode, String bizType, Integer direction, List<ThirdPartyFieldMap> maps) {
        if (partnerCode == null || partnerCode.trim().isEmpty()) {
            throw new BusinessException("对接方编码不能为空");
        }
        if (bizType == null || bizType.trim().isEmpty()) {
            throw new BusinessException("业务类型不能为空");
        }
        int dir = direction == null ? 1 : direction;
        fieldMapMapper.delete(new LambdaQueryWrapper<ThirdPartyFieldMap>()
                .eq(ThirdPartyFieldMap::getPartnerCode, partnerCode)
                .eq(ThirdPartyFieldMap::getBizType, bizType)
                .eq(ThirdPartyFieldMap::getDirection, dir));
        if (maps == null) {
            return;
        }
        for (ThirdPartyFieldMap map : maps) {
            if (map.getLocalField() == null || map.getLocalField().trim().isEmpty()
                    || map.getRemoteField() == null || map.getRemoteField().trim().isEmpty()) {
                continue;
            }
            map.setId(null);
            map.setPartnerCode(partnerCode);
            map.setBizType(bizType);
            map.setDirection(dir);
            map.setCreateTime(LocalDateTime.now());
            fieldMapMapper.insert(map);
        }
    }

    public void delete(Long id) {
        fieldMapMapper.deleteById(id);
    }
}
