package com.lis.instrumentmgmt.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.IService;
import com.lis.instrumentmgmt.entity.MaintenanceTemplate;
import com.lis.instrumentmgmt.entity.MaintenanceTemplateItem;

import java.util.List;

/**
 * 仪器维护模板服务
 */
public interface MaintenanceTemplateService extends IService<MaintenanceTemplate> {

    /**
     * 分页查询模板
     */
    Page<MaintenanceTemplate> queryPage(long current, long size, String keyword, String cycleType);

    /**
     * 根据模板编码获取模板
     */
    MaintenanceTemplate getByCode(String templateCode);

    /**
     * 加载模板明细
     */
    List<MaintenanceTemplateItem> listItems(Long templateId);

    /**
     * 保存模板明细 (覆盖)
     */
    void saveItems(Long templateId, List<MaintenanceTemplateItem> items);
}
