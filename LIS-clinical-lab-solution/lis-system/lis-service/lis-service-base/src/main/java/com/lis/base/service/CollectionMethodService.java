package com.lis.base.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.IService;
import com.lis.base.entity.CollectionMethod;

import java.util.List;

/**
 * 采集方式 Service
 */
public interface CollectionMethodService extends IService<CollectionMethod> {

    /**
     * 分页查询
     */
    Page<CollectionMethod> queryPage(long current, long size, String keyword, Integer status);

    /**
     * 根据编码查询
     */
    CollectionMethod getByCode(String methodCode);

    /**
     * 新增
     */
    CollectionMethod create(CollectionMethod collectionMethod);

    /**
     * 更新
     */
    CollectionMethod update(CollectionMethod collectionMethod);

    /**
     * 删除
     */
    void delete(Long id);

    /**
     * 查询全部启用的采集方式 (供下拉选择)
     */
    List<CollectionMethod> listAllEnabled();
}
