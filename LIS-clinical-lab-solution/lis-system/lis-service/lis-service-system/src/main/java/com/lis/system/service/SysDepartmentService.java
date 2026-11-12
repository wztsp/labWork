package com.lis.system.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.lis.system.entity.SysDepartment;

import java.util.List;

/**
 * 科室服务接口 - CRUD + 树形查询
 */
public interface SysDepartmentService extends IService<SysDepartment> {

    /**
     * 树形列表
     */
    List<SysDepartment> listTree();

    /**
     * 新增
     */
    SysDepartment create(SysDepartment dept);

    /**
     * 修改
     */
    void update(SysDepartment dept);

    /**
     * 删除 (递归删除子节点)
     */
    void delete(Long id);
}
