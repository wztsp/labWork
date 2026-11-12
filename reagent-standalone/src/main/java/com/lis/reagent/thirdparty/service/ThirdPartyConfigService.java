package com.lis.reagent.thirdparty.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.lis.common.core.exception.BusinessException;
import com.lis.reagent.thirdparty.entity.ThirdPartyConfig;
import com.lis.reagent.mapper.ThirdPartyConfigMapper;
import com.lis.reagent.thirdparty.support.ThirdPartyHttpClient;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 三方卫材对接配置服务。
 */
@Service
@RequiredArgsConstructor
public class ThirdPartyConfigService {

    private final ThirdPartyConfigMapper configMapper;
    private final ThirdPartyHttpClient httpClient;

    public Page<ThirdPartyConfig> queryPage(long current, long size, String keyword, Integer enabled) {
        LambdaQueryWrapper<ThirdPartyConfig> wrapper = new LambdaQueryWrapper<>();
        if (keyword != null && !keyword.trim().isEmpty()) {
            wrapper.and(w -> w.like(ThirdPartyConfig::getPartnerCode, keyword)
                    .or().like(ThirdPartyConfig::getPartnerName, keyword));
        }
        if (enabled != null) {
            wrapper.eq(ThirdPartyConfig::getEnabled, enabled);
        }
        wrapper.orderByDesc(ThirdPartyConfig::getId);
        return configMapper.selectPage(new Page<>(current, size), wrapper);
    }

    public List<ThirdPartyConfig> listAll() {
        return configMapper.selectList(new LambdaQueryWrapper<ThirdPartyConfig>()
                .orderByDesc(ThirdPartyConfig::getId));
    }

    public ThirdPartyConfig getById(Long id) {
        ThirdPartyConfig config = configMapper.selectById(id);
        if (config == null) {
            throw new BusinessException("对接配置不存在: " + id);
        }
        return config;
    }

    /** 按对接方编码取配置，不校验启用状态（供入站接口使用）。 */
    public ThirdPartyConfig getByCode(String partnerCode) {
        ThirdPartyConfig config = configMapper.selectOne(new LambdaQueryWrapper<ThirdPartyConfig>()
                .eq(ThirdPartyConfig::getPartnerCode, partnerCode));
        if (config == null) {
            throw new BusinessException("未配置对接方: " + partnerCode);
        }
        return config;
    }

    /** 取启用状态的对接配置，停用或不存在时拒绝调用。 */
    public ThirdPartyConfig requireEnabled(String partnerCode) {
        ThirdPartyConfig config = getByCode(partnerCode);
        if (config.getEnabled() == null || config.getEnabled() != 1) {
            throw new BusinessException("对接方已停用: " + partnerCode);
        }
        if (config.getBaseUrl() == null || config.getBaseUrl().trim().isEmpty()) {
            throw new BusinessException("对接方未配置接口地址: " + partnerCode);
        }
        return config;
    }

    public ThirdPartyConfig save(ThirdPartyConfig config) {
        if (config.getPartnerCode() == null || config.getPartnerCode().trim().isEmpty()) {
            throw new BusinessException("对接方编码不能为空");
        }
        if (config.getPartnerName() == null || config.getPartnerName().trim().isEmpty()) {
            throw new BusinessException("对接方名称不能为空");
        }
        ThirdPartyConfig exists = configMapper.selectOne(new LambdaQueryWrapper<ThirdPartyConfig>()
                .eq(ThirdPartyConfig::getPartnerCode, config.getPartnerCode().trim()));
        if (exists != null && !exists.getId().equals(config.getId())) {
            throw new BusinessException("对接方编码已存在: " + config.getPartnerCode());
        }
        config.setPartnerCode(config.getPartnerCode().trim());
        config.setUpdateTime(LocalDateTime.now());
        if (config.getId() == null) {
            config.setCreateTime(LocalDateTime.now());
            if (config.getEnabled() == null) {
                config.setEnabled(0);
            }
            if (config.getInboundEnabled() == null) {
                config.setInboundEnabled(0);
            }
            configMapper.insert(config);
        } else {
            configMapper.updateById(config);
        }
        return config;
    }

    public void delete(Long id) {
        configMapper.deleteById(id);
    }

    /** 连通性测试：按配置的鉴权方式请求对方根地址。 */
    public String testConnection(Long id, String operatorName) {
        return httpClient.ping(getById(id), operatorName);
    }
}
