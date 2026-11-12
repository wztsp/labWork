package com.lis.reagent.thirdparty.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.lis.common.core.exception.BusinessException;
import com.lis.reagent.entity.Reagent;
import com.lis.reagent.mapper.ReagentMapper;
import com.lis.reagent.thirdparty.entity.ThirdPartyProductMap;
import com.lis.reagent.mapper.ThirdPartyProductMapMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 三方卫材商品映射服务：维护本系统试剂与对方商品编码的对应关系。
 */
@Service
@RequiredArgsConstructor
public class ThirdPartyProductMapService {

    private final ThirdPartyProductMapMapper productMapMapper;
    private final ReagentMapper reagentMapper;

    public Page<ThirdPartyProductMap> queryPage(long current, long size, String partnerCode,
                                                String keyword, Integer mapped) {
        LambdaQueryWrapper<ThirdPartyProductMap> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(partnerCode != null && !partnerCode.isEmpty(), ThirdPartyProductMap::getPartnerCode, partnerCode);
        if (keyword != null && !keyword.trim().isEmpty()) {
            wrapper.and(w -> w.like(ThirdPartyProductMap::getRemoteCode, keyword)
                    .or().like(ThirdPartyProductMap::getRemoteName, keyword)
                    .or().like(ThirdPartyProductMap::getReagentCode, keyword)
                    .or().like(ThirdPartyProductMap::getReagentName, keyword));
        }
        if (mapped != null) {
            if (mapped == 1) {
                wrapper.isNotNull(ThirdPartyProductMap::getReagentId);
            } else {
                wrapper.isNull(ThirdPartyProductMap::getReagentId);
            }
        }
        wrapper.orderByDesc(ThirdPartyProductMap::getId);
        return productMapMapper.selectPage(new Page<>(current, size), wrapper);
    }

    public ThirdPartyProductMap save(ThirdPartyProductMap map) {
        if (map.getPartnerCode() == null || map.getPartnerCode().trim().isEmpty()) {
            throw new BusinessException("对接方编码不能为空");
        }
        if (map.getRemoteCode() == null || map.getRemoteCode().trim().isEmpty()) {
            throw new BusinessException("对方商品编码不能为空");
        }
        if (map.getReagentId() != null) {
            Reagent reagent = reagentMapper.selectById(map.getReagentId());
            if (reagent == null) {
                throw new BusinessException("试剂不存在: " + map.getReagentId());
            }
            map.setReagentCode(reagent.getReagentCode());
            map.setReagentName(reagent.getReagentName());
        }
        ThirdPartyProductMap exists = productMapMapper.selectOne(new LambdaQueryWrapper<ThirdPartyProductMap>()
                .eq(ThirdPartyProductMap::getPartnerCode, map.getPartnerCode())
                .eq(ThirdPartyProductMap::getRemoteCode, map.getRemoteCode().trim()));
        if (exists != null && !exists.getId().equals(map.getId())) {
            throw new BusinessException("对方商品编码已映射: " + map.getRemoteCode());
        }
        map.setRemoteCode(map.getRemoteCode().trim());
        map.setUpdateTime(LocalDateTime.now());
        if (map.getId() == null) {
            map.setCreateTime(LocalDateTime.now());
            productMapMapper.insert(map);
        } else {
            productMapMapper.updateById(map);
        }
        return map;
    }

    public void delete(Long id) {
        productMapMapper.deleteById(id);
    }

    /** 按对方商品编码反查本系统试剂 ID，未维护映射时回退按试剂编码匹配。 */
    public Long resolveReagentId(String partnerCode, String remoteCode) {
        if (remoteCode == null || remoteCode.trim().isEmpty()) {
            return null;
        }
        ThirdPartyProductMap map = productMapMapper.selectOne(new LambdaQueryWrapper<ThirdPartyProductMap>()
                .eq(ThirdPartyProductMap::getPartnerCode, partnerCode)
                .eq(ThirdPartyProductMap::getRemoteCode, remoteCode.trim()));
        if (map != null && map.getReagentId() != null) {
            return map.getReagentId();
        }
        Reagent reagent = findReagentByCode(remoteCode.trim());
        return reagent == null ? null : reagent.getId();
    }

    /** 自动匹配：对方商品编码与本系统试剂编码一致的直接建立映射。 */
    public int autoMatch(String partnerCode) {
        List<ThirdPartyProductMap> unmapped = productMapMapper.selectList(
                new LambdaQueryWrapper<ThirdPartyProductMap>()
                        .eq(ThirdPartyProductMap::getPartnerCode, partnerCode)
                        .isNull(ThirdPartyProductMap::getReagentId));
        int matched = 0;
        for (ThirdPartyProductMap map : unmapped) {
            Reagent reagent = findReagentByCode(map.getRemoteCode());
            if (reagent == null) {
                continue;
            }
            map.setReagentId(reagent.getId());
            map.setReagentCode(reagent.getReagentCode());
            map.setReagentName(reagent.getReagentName());
            map.setUpdateTime(LocalDateTime.now());
            productMapMapper.updateById(map);
            matched++;
        }
        return matched;
    }

    /** 同步对方商品主数据：按对方编码新增或更新。 */
    public int upsertFromRemote(String partnerCode, List<ThirdPartyProductMap> remoteList) {
        int count = 0;
        for (ThirdPartyProductMap remote : remoteList) {
            if (remote.getRemoteCode() == null || remote.getRemoteCode().trim().isEmpty()) {
                continue;
            }
            ThirdPartyProductMap exists = productMapMapper.selectOne(
                    new LambdaQueryWrapper<ThirdPartyProductMap>()
                            .eq(ThirdPartyProductMap::getPartnerCode, partnerCode)
                            .eq(ThirdPartyProductMap::getRemoteCode, remote.getRemoteCode().trim()));
            ThirdPartyProductMap target = exists == null ? new ThirdPartyProductMap() : exists;
            target.setPartnerCode(partnerCode);
            target.setRemoteCode(remote.getRemoteCode().trim());
            target.setRemoteName(remote.getRemoteName());
            target.setRemoteSpec(remote.getRemoteSpec());
            target.setRemoteUnit(remote.getRemoteUnit());
            target.setRemotePrice(remote.getRemotePrice());
            target.setLastSyncTime(LocalDateTime.now());
            target.setUpdateTime(LocalDateTime.now());
            if (target.getReagentId() == null) {
                Reagent reagent = findReagentByCode(target.getRemoteCode());
                if (reagent != null) {
                    target.setReagentId(reagent.getId());
                    target.setReagentCode(reagent.getReagentCode());
                    target.setReagentName(reagent.getReagentName());
                }
            }
            if (exists == null) {
                target.setCreateTime(LocalDateTime.now());
                productMapMapper.insert(target);
            } else {
                productMapMapper.updateById(target);
            }
            count++;
        }
        return count;
    }

    /** 按本系统试剂 ID 查对方商品编码，未维护映射时返回 null。 */
    public ThirdPartyProductMap findByReagentId(String partnerCode, Long reagentId) {
        if (reagentId == null) {
            return null;
        }
        return productMapMapper.selectOne(new LambdaQueryWrapper<ThirdPartyProductMap>()
                .eq(ThirdPartyProductMap::getPartnerCode, partnerCode)
                .eq(ThirdPartyProductMap::getReagentId, reagentId)
                .last("limit 1"));
    }

    private Reagent findReagentByCode(String code) {
        if (code == null || code.trim().isEmpty()) {
            return null;
        }
        return reagentMapper.selectOne(new LambdaQueryWrapper<Reagent>()
                .eq(Reagent::getReagentCode, code.trim())
                .last("limit 1"));
    }
}
