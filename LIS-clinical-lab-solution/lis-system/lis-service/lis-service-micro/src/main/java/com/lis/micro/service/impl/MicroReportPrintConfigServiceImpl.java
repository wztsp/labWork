package com.lis.micro.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.lis.common.core.exception.BusinessException;
import com.lis.micro.entity.MicroReportPrintConfig;
import com.lis.micro.mapper.MicroReportPrintConfigMapper;
import com.lis.micro.service.MicroReportPrintConfigService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 微生物报告打印模板配置服务实现
 */
@Service
public class MicroReportPrintConfigServiceImpl extends ServiceImpl<MicroReportPrintConfigMapper, MicroReportPrintConfig>
        implements MicroReportPrintConfigService {

    @Override
    public List<MicroReportPrintConfig> listAll() {
        return this.list(new LambdaQueryWrapper<MicroReportPrintConfig>()
                .orderByDesc(MicroReportPrintConfig::getIsDefault));
    }

    @Override
    public MicroReportPrintConfig getDefault() {
        return this.getOne(new LambdaQueryWrapper<MicroReportPrintConfig>()
                .eq(MicroReportPrintConfig::getIsDefault, 1)
                .last("LIMIT 1"));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public MicroReportPrintConfig create(MicroReportPrintConfig config) {
        if (config.getConfigName() == null || config.getConfigName().isEmpty()) {
            throw new BusinessException("配置名称不能为空");
        }
        config.setCreateTime(LocalDateTime.now());
        if (config.getIsDefault() == null) {
            config.setIsDefault(0);
        }
        // 若设为默认，先清除其它默认
        if (Integer.valueOf(1).equals(config.getIsDefault())) {
            this.update(new LambdaUpdateWrapper<MicroReportPrintConfig>()
                    .eq(MicroReportPrintConfig::getIsDefault, 1)
                    .set(MicroReportPrintConfig::getIsDefault, 0));
        }
        this.save(config);
        return config;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public MicroReportPrintConfig update(MicroReportPrintConfig config) {
        if (config.getId() == null) {
            throw new BusinessException("ID不能为空");
        }
        this.updateById(config);
        return config;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        this.removeById(id);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void setDefault(Long id) {
        MicroReportPrintConfig config = this.getById(id);
        if (config == null) {
            throw new BusinessException("配置不存在: " + id);
        }
        // 清除其它默认
        this.update(new LambdaUpdateWrapper<MicroReportPrintConfig>()
                .eq(MicroReportPrintConfig::getIsDefault, 1)
                .set(MicroReportPrintConfig::getIsDefault, 0));
        // 设置当前为默认
        config.setIsDefault(1);
        this.updateById(config);
    }
}
