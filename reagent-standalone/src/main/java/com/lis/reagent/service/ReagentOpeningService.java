package com.lis.reagent.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.lis.reagent.entity.ReagentOpening;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public interface ReagentOpeningService extends IService<ReagentOpening> {

    /** 开瓶登记: 计算开瓶效期 */
    ReagentOpening createOpening(ReagentOpening opening);

    /** 锁定已过开瓶效期的记录 */
    int lockExpiredOpenings();

    /** 分页查询（支持试剂、状态、开封类型、开瓶日期区间） */
    Page<ReagentOpening> queryPage(long current, long size, Long reagentId, Integer status,
                                   Integer openingType, LocalDate startDate, LocalDate endDate);

    /** 二次开封：基于首次开封或已闭封记录生成 opening_type=2 的新记录，来源记录保持原状 */
    ReagentOpening reopen(Long sourceId, Long operatorId, String operatorName);

    /** 闭封：状态置为已闭封(2) */
    void seal(Long id, String operatorName);

    /** 耗尽：状态置为已耗尽(3) */
    void exhaust(Long id);

    /** 质控品分包：生成 packCount+1 条分包记录 */
    List<ReagentOpening> subPackage(Long stockId, Long reagentId, Integer packCount,
                                    Long operatorId, String operatorName);

    /** 危化品开封申请：生成待审批记录 */
    ReagentOpening applyHazardOpening(Long reagentId, Long stockId, BigDecimal demandQty,
                                      Long operatorId, String operatorName, String remark);

    /** 危化品开封审批通过 */
    void approveHazard(Long id, Long approverId, String approverName, String opinion);

    /** 危化品开封审批驳回 */
    void rejectHazard(Long id, Long approverId, String approverName, String opinion);
}
