package com.lis.qc.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.lis.common.core.result.PageResult;
import com.lis.qc.entity.OccEvent;
import com.lis.qc.entity.QCLot;
import com.lis.qc.entity.QCRecord;
import com.lis.qc.service.rule.QCResult;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;

/**
 * 失控报告(OCC)服务: 事件建档、七步法处置、根因确认、三级审批。
 * 依据 occ-module-design.md。
 */
public interface OccService {

    PageResult<OccEvent> page(long current, long size, String status, String keyword, String range);

    OccEvent detail(Long id);

    @Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Exception.class)
    OccEvent create(OccEvent event, String operator);

    /**
     * 由失控判定(REJECT)自动产生失控报告并关联质控批号(lot_id)。
     * 同一批号已有未关闭(PENDING/INVESTIGATING/CORRECTED)事件时复用并回填触发规则/质控值，
     * 否则新建事件并初始化七步法与三级审批。
     *
     * @param lot 触发失控的质控品批号 (取靶值/SD, 并以 testItemId 回填检验项目)
     * @param record 触发失控的质控记录 (取实测值/测量时间)
     * @param result Westgard 判定结果 (触发规则)
     * @param operator 触发操作人
     * @return 失控报告事件(含步骤与审批)
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Exception.class)
    OccEvent createFromReject(QCLot lot, QCRecord record, QCResult result, String operator);

    /** 更新指定步骤的 checklist/结论, 全部勾选后置 done 并推进到下一步 */
    OccEvent updateStep(Long id, Integer stepNo, Map<String, Object> payload, String operator);

    /** 根因确认: 校验步骤4排查项全部完成, 写入根因并推进 */
    OccEvent confirmRootCause(Long id, Map<String, Object> payload, String operator);

    /** 审批: levelNo 1/2/3, approved 是否通过 */
    OccEvent approve(Long id, String approver, Integer levelNo, boolean approved, String comment);

    /** 全部事件(供统计) */
    List<OccEvent> listAllEvents();
}