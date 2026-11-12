package com.lis.reagent.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.IService;
import com.lis.reagent.dto.ApprovalDTO;
import com.lis.reagent.dto.RequisitionCreateDTO;
import com.lis.reagent.dto.StockOpContext;
import com.lis.reagent.entity.ReagentRequisition;

/**
 * 统一出入库业务单服务（领用 / 退库 / 报废 / 调拨 / 借用 / 归还六类合一）。
 *
 * <p><b>状态机</b>：{@code com.lis.reagent.enums.ReqStatus}
 * （0 草稿 → 1 待审 → 2 已审 → 3 已执行；4 已驳回；5 已作废）。
 * 提交后按 {@code RequisitionTypeSpec.requiresApproval} 决定免审（普通，Q6 默认）或进审批。</p>
 *
 * <p><b>执行器</b>：{@link #execute} 按类型规约的 {@code direction} 三分支分派
 * （IN / OUT / MOVE），<b>严禁 switch(reqType)</b>（设计文档 §2.3）。执行与库存变更
 * 同一事务，库存不足整体回滚，单据保持已审。</p>
 *
 * @author LIS Team
 */
public interface ReagentRequisitionService extends IService<ReagentRequisition> {

    /**
     * 创建业务单（草稿）。
     *
     * @param dto 单据 + 明细
     * @return 创建后的业务单
     */
    ReagentRequisition create(RequisitionCreateDTO dto);

    /**
     * 提交（免审直接已审，否则进入待审）。
     *
     * @param id  业务单 ID
     * @param ctx 业务上下文
     */
    void submit(Long id, StockOpContext ctx);

    /**
     * 审批通过（待审 → 已审）。
     *
     * @param id  业务单 ID
     * @param dto 审批信息
     */
    void approve(Long id, ApprovalDTO dto);

    /**
     * 审批驳回（待审 → 已驳回）。
     *
     * @param id  业务单 ID
     * @param dto 审批信息
     */
    void reject(Long id, ApprovalDTO dto);

    /**
     * 执行（已审 → 已执行）：按类型规约方向分派库存变动，写流水并落科室归属。
     *
     * @param id  业务单 ID
     * @param ctx 业务上下文（deptId/deptName 落流水）
     */
    void execute(Long id, StockOpContext ctx);

    /**
     * 作废（任意非已执行态 → 已作废）。
     *
     * @param id           业务单 ID
     * @param operatorName 操作人姓名
     */
    void cancel(Long id, String operatorName);

    /**
     * 分页查询。
     *
     * @param current 页码
     * @param size    页大小
     * @param reqType 类型，可为 null
     * @param status  状态，可为 null
     * @param reqNo   单号模糊，可为 null
     * @param deptId  科室，可为 null
     * @return 分页结果
     */
    Page<ReagentRequisition> queryPage(long current, long size, Integer reqType, Integer status,
                                       String reqNo, Long deptId);

    /**
     * 详情（含明细）。
     *
     * @param id 业务单 ID
     * @return 业务单实体（含 items）
     */
    ReagentRequisition getDetail(Long id);
}
