package com.lis.system.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.lis.common.core.exception.BusinessException;
import com.lis.system.entity.SysConfig;
import com.lis.system.mapper.SysConfigMapper;
import com.lis.system.service.SysConfigService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 系统参数服务实现
 */
@Service
public class SysConfigServiceImpl extends ServiceImpl<SysConfigMapper, SysConfig> implements SysConfigService {

    @Override
    public List<SysConfig> listAll() {
        return this.list(new LambdaQueryWrapper<SysConfig>()
                .orderByDesc(SysConfig::getCreateTime));
    }

    @Override
    public SysConfig getByKey(String configKey) {
        if (!StringUtils.hasText(configKey)) {
            throw new BusinessException(400, "参数 key 不能为空");
        }
        return this.getOne(new LambdaQueryWrapper<SysConfig>()
                .eq(SysConfig::getConfigKey, configKey));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public SysConfig create(SysConfig config) {
        if (config == null || !StringUtils.hasText(config.getConfigKey())) {
            throw new BusinessException(400, "参数 key 不能为空");
        }
        Long count = this.lambdaQuery().eq(SysConfig::getConfigKey, config.getConfigKey()).count();
        if (count != null && count > 0) {
            throw new BusinessException(400, "参数 key 已存在");
        }
        config.setCreateTime(LocalDateTime.now());
        config.setUpdateTime(LocalDateTime.now());
        this.save(config);
        return config;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void update(SysConfig config) {
        if (config == null || config.getId() == null) {
            throw new BusinessException(400, "参数 ID 不能为空");
        }
        config.setUpdateTime(LocalDateTime.now());
        this.updateById(config);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        if (id == null) {
            throw new BusinessException(400, "参数 ID 不能为空");
        }
        this.removeById(id);
    }
}
