package com.lis.base.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.IService;
import com.lis.base.entity.TestItem;

import java.util.List;

/**
 * 检验项目 Service
 */
public interface TestItemService extends IService<TestItem> {

    /**
     * 分页查询检验项目
     */
    Page<TestItem> queryPage(long current, long size, String keyword, String category, Integer isPanel, Integer status, String specimenType);

    /**
     * 根据编码查询
     */
    TestItem getByCode(String itemCode);

    /**
     * 新增项目
     */
    TestItem create(TestItem testItem);

    /**
     * 更新项目
     */
    TestItem update(TestItem testItem);

    /**
     * 删除项目
     */
    void delete(Long id);

    /**
     * 查询检验项目下的子项目
     */
    List<TestItem> listPanelItems(Long parentId);

    /**
     * 管理检验项目(设置子项目)
     */
    void managePanel(Long parentId, List<Long> childIds);

    /**
     * 从缓存加载全部项目字典
     */
    List<TestItem> loadDictFromCache();
}
