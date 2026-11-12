package com.lis.reagent.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.lis.reagent.entity.ReagentScrapDisposal;

/**
 * 报废单环保处置信息服务（T-15 高危审批 + 环保处置）。
 *
 * @author LIS Team
 */
public interface ReagentScrapDisposalService {

    /** 按报废单查询处置信息（无则返回 null） */
    ReagentScrapDisposal getByReqId(Long reqId);

    /** 保存 / 更新处置信息（按 reqId 幂等 upsert） */
    ReagentScrapDisposal save(ReagentScrapDisposal dto);

    /** 分页查询处置台账 */
    Page<ReagentScrapDisposal> page(long current, long size);

    /** 删除处置登记 */
    void deleteById(Long id);
}