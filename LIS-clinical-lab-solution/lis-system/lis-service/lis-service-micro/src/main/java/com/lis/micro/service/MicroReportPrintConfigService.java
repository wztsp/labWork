package com.lis.micro.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.lis.micro.entity.MicroReportPrintConfig;

import java.util.List;

/**
 * 微生物报告打印模板配置服务
 */
public interface MicroReportPrintConfigService extends IService<MicroReportPrintConfig> {

    List<MicroReportPrintConfig> listAll();

    MicroReportPrintConfig getDefault();

    MicroReportPrintConfig create(MicroReportPrintConfig config);

    MicroReportPrintConfig update(MicroReportPrintConfig config);

    void delete(Long id);

    /**
     * 设为默认模板
     */
    void setDefault(Long id);
}
