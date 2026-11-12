package com.lis.micro.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.IService;
import com.lis.micro.entity.MediumType;

/**
 * 培养基类型字典服务
 */
public interface MediumTypeService extends IService<MediumType> {

    /** 分页查询 */
    Page<MediumType> queryPage(long current, long size, String keyword);

    /** 全部启用培养基 */
    java.util.List<MediumType> listEnabled();
}