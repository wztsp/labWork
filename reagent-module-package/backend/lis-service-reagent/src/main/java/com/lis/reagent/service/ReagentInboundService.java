package com.lis.reagent.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.lis.reagent.entity.ReagentInbound;

public interface ReagentInboundService extends IService<ReagentInbound> {

    /** 创建入库单(含明细) */
    ReagentInbound createInbound(ReagentInbound inbound);

    /** 验收入库单: 状态0→1, 将明细写入库存 */
    ReagentInbound verifyInbound(Long id, Long verifierId, String verifierName);

    /** 拒收入库单: 状态0→2 */
    ReagentInbound rejectInbound(Long id, Long verifierId, String verifierName, String reason);

    /** 查询入库单详情(含明细) */
    ReagentInbound getDetail(Long id);

    /** 分页查询 */
    Page<ReagentInbound> queryPage(long current, long size, String inboundNo, Integer verifyStatus);
}
