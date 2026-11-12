package com.lis.reagent.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.lis.reagent.entity.ReagentConsumption;

public interface ReagentConsumptionService extends IService<ReagentConsumption> {

    /** 手工消耗登记: 扣减库存 + 写消耗单 + 写流水 */
    ReagentConsumption createConsumption(ReagentConsumption consumption);

    /** C2自动扣减: 按检验项目×次数查找关联试剂并扣减 */
    void autoConsume(Long testItemId, Integer testCount, Long operatorId, String operatorName, String relatedNo);

    /** 分页查询 */
    Page<ReagentConsumption> queryPage(long current, long size, Long reagentId, Integer consumeType, String relatedNo);
}
