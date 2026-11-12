package com.lis.reagent.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.lis.reagent.entity.ReagentInbound;
import com.lis.reagent.entity.ReagentInboundItem;

import java.util.List;
import java.util.Map;

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

    /**
     * 扫码解析条码：先匹配试剂条码别名，再匹配库存批次条码。
     *
     * @param code 扫码内容
     * @return 含 reagentId / reagentName / batchNo / stockId 的结果
     */
    Map<String, Object> resolveScanCode(String code);

    /**
     * 创建扫码入库单（待验收，后续走原有验收流程入库）。
     *
     * @param inboundMode 入库方式
     * @param items       入库明细（每个条码一条）
     * @param remark      备注
     * @return 创建后的入库单
     */
    ReagentInbound createScanInbound(String inboundMode, List<ReagentInboundItem> items, String remark);
}
