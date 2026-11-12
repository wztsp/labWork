package com.lis.system.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.lis.system.entity.SysConfig;

import java.util.List;

/**
 * 系统参数服务接口
 */
public interface SysConfigService extends IService<SysConfig> {

    /**
     * 列表
     */
    List<SysConfig> listAll();

    /**
     * 按 key 查询
     */
    SysConfig getByKey(String configKey);

    /**
     * 新增
     */
    SysConfig create(SysConfig config);

    /**
     * 修改
     */
    void update(SysConfig config);

    /**
     * 删除
     */
    void delete(Long id);
}
