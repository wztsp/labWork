package com.lis.micro.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.IService;
import com.lis.micro.entity.MicroQcRecord;

import java.util.Map;

/**
 * 微生物室内质控服务
 */
public interface MicroQcService extends IService<MicroQcRecord> {

    /** 分页查询 */
    Page<MicroQcRecord> queryPage(long current, long size, Integer itemType, String keyword, String result);

    /** 保存 (新增/更新), 自动根据预期/实际结果判定并记录操作人 */
    MicroQcRecord saveRecord(MicroQcRecord record);

    /** 指定类别质控统计 */
    Map<String, Object> stats(Integer itemType);
}