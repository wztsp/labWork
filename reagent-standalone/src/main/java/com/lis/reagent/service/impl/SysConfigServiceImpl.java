package com.lis.reagent.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.lis.common.core.exception.BusinessException;
import com.lis.reagent.entity.SysConfig;
import com.lis.reagent.mapper.SysConfigMapper;
import com.lis.reagent.service.SysConfigService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 系统参数配置服务实现。
 */
@Service
@RequiredArgsConstructor
public class SysConfigServiceImpl extends ServiceImpl<SysConfigMapper, SysConfig>
        implements SysConfigService {

    @Override
    public List<SysConfig> listAll() {
        return this.list(new LambdaQueryWrapper<SysConfig>()
                .orderByAsc(SysConfig::getConfigKey));
    }

    @Override
    public String getValue(String key) {
        SysConfig config = this.getOne(new LambdaQueryWrapper<SysConfig>()
                .eq(SysConfig::getConfigKey, key));
        return config == null ? null : config.getConfigValue();
    }

    @Override
    public boolean getBoolean(String key, boolean defaultValue) {
        String value = getValue(key);
        if (value == null || value.trim().isEmpty()) {
            return defaultValue;
        }
        String v = value.trim();
        if ("1".equals(v) || "true".equalsIgnoreCase(v) || "yes".equalsIgnoreCase(v)
                || "on".equalsIgnoreCase(v)) {
            return true;
        }
        if ("0".equals(v) || "false".equalsIgnoreCase(v) || "no".equalsIgnoreCase(v)
                || "off".equalsIgnoreCase(v)) {
            return false;
        }
        return defaultValue;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateValue(String configKey, String configValue) {
        SysConfig config = this.getOne(new LambdaQueryWrapper<SysConfig>()
                .eq(SysConfig::getConfigKey, configKey));
        if (config == null) {
            throw new BusinessException("参数不存在");
        }
        config.setConfigValue(configValue);
        config.setUpdateTime(LocalDateTime.now());
        this.updateById(config);
    }
}
