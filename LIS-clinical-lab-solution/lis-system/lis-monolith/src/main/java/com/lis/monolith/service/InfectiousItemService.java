package com.lis.monolith.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.lis.monolith.entity.InfectiousItem;

import java.util.List;

/**
 * 传染病项目配置服务
 */
public interface InfectiousItemService {

    IPage<InfectiousItem> page(int page, int size, String keyword);

    List<InfectiousItem> listAll();

    InfectiousItem create(InfectiousItem item);

    InfectiousItem update(InfectiousItem item);

    void delete(Long id);
}