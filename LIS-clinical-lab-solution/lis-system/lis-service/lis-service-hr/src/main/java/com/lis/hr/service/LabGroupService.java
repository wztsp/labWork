package com.lis.hr.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.lis.hr.entity.LabGroup;

import java.util.List;

/**
 * 小组/科室服务
 */
public interface LabGroupService extends IService<LabGroup> {

    List<LabGroup> listAll();

    List<LabGroup> listTree();

    LabGroup create(LabGroup group);

    LabGroup update(LabGroup group);

    void delete(Long id);
}
