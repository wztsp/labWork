package com.lis.base.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.IService;
import com.lis.base.entity.SpecimenType;

import java.util.List;

/**
 * 标本类型 Service
 */
public interface SpecimenTypeService extends IService<SpecimenType> {

    /**
     * 分页查询
     */
    Page<SpecimenType> queryPage(long current, long size, String keyword, Integer status);

    /**
     * 根据编码查询
     */
    SpecimenType getByCode(String typeCode);

    /**
     * 新增
     */
    SpecimenType create(SpecimenType specimenType);

    /**
     * 更新
     */
    SpecimenType update(SpecimenType specimenType);

    /**
     * 删除
     */
    void delete(Long id);

    /**
     * 查询全部启用的标本类型 (供下拉选择)
     */
    List<SpecimenType> listAllEnabled();
}
