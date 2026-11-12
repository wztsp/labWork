package com.lis.base.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.IService;
import com.lis.base.entity.TestTube;

import java.util.List;

/**
 * 检验试管 Service
 */
public interface TestTubeService extends IService<TestTube> {

    /**
     * 分页查询
     */
    Page<TestTube> queryPage(long current, long size, String keyword, String tubeType, Integer status);

    /**
     * 根据编码查询
     */
    TestTube getByCode(String tubeCode);

    /**
     * 新增
     */
    TestTube create(TestTube testTube);

    /**
     * 更新
     */
    TestTube update(TestTube testTube);

    /**
     * 删除
     */
    void delete(Long id);

    /**
     * 查询全部启用的试管 (供下拉选择)
     */
    List<TestTube> listAllEnabled();
}
