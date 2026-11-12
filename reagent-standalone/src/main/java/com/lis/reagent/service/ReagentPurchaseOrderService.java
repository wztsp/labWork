package com.lis.reagent.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.IService;
import com.lis.reagent.dto.OrderCreateDTO;
import com.lis.reagent.dto.PurchaseOrderMergeDTO;
import com.lis.reagent.dto.StockOpContext;
import com.lis.reagent.entity.ReagentPurchaseOrder;

/**
 * 采购订单服务（R-02 采购闭环）。
 *
 * <p><b>生命周期</b>：已批准申请单 → {@link #createFromApply} 建单（待下单）→
 * {@link #placeOrder} 下单 → 到货验收（由入库单 {@code verifyInbound} 回写
 * {@code arrivedQty} 并推进状态）→ 全到齐「已到货」+ 预算实扣；任意非到货态
 * {@link #cancel} 取消并释放预算。</p>
 *
 * <p><b>状态机</b>：{@code com.lis.reagent.enums.OrderStatus}
 * （0 待下单 / 1 已下单 / 2 部分到货 / 3 已到货 / 4 已取消）。</p>
 *
 * <p><b>比价</b>：跨供应商历史比价见 {@code SupplierService#comparePrice}，
 * 本服务不再重复实现。</p>
 *
 * @author LIS Team
 */
public interface ReagentPurchaseOrderService extends IService<ReagentPurchaseOrder> {

    /**
     * 由已批准申请单创建采购订单。
     *
     * @param dto 订单入参（含来源申请 ID 与供应商 ID，供应商必填）
     * @return 创建后的订单
     */
    ReagentPurchaseOrder createFromApply(OrderCreateDTO dto);

    /**
     * 合并多张已批准申请生成一张采购订单。
     *
     * @param dto 合并入参（申请 ID 列表 + 供应商）
     * @return 创建后的订单
     */
    ReagentPurchaseOrder merge(PurchaseOrderMergeDTO dto);

    /**
     * 订单审批通过。
     *
     * @param id      订单 ID
     * @param opinion 审批意见
     */
    void approve(Long id, String opinion);

    /**
     * 订单审批驳回。
     *
     * @param id      订单 ID
     * @param opinion 审批意见
     */
    void reject(Long id, String opinion);

    /**
     * 订单审核：审批通过且无需审批开关时置为已审核并记录审核人。
     *
     * @param id           订单 ID
     * @param auditorId    审核人 ID，可为 null
     * @param auditorName  审核人姓名，可为 null
     */
    void audit(Long id, Long auditorId, String auditorName);

    /**
     * 取消审核（仅已审核订单可取消）。
     *
     * @param id          订单 ID
     * @param auditorId   操作人 ID，可为 null
     * @param auditorName 操作人姓名，可为 null
     */
    void unaudit(Long id, Long auditorId, String auditorName);

    /**
     * 下单：待下单 → 已下单。
     *
     * @param id  订单 ID
     * @param ctx 业务上下文
     */
    void placeOrder(Long id, StockOpContext ctx);

    /**
     * 取消订单（仅允许在未到货状态下取消），并释放预算占用。
     *
     * @param id  订单 ID
     * @param ctx 业务上下文
     */
    void cancel(Long id, StockOpContext ctx);

    /**
     * 分页查询。
     *
     * @param current    页码
     * @param size       页大小
     * @param orderNo    单号模糊，可为 null
     * @param status     状态，可为 null
     * @param applyId    来源申请，可为 null
     * @param supplierId 供应商，可为 null
     * @return 分页结果
     */
    Page<ReagentPurchaseOrder> queryPage(long current, long size, String orderNo, Integer status,
                                         Long applyId, Long supplierId);

    /**
     * 订单详情（含明细）。
     *
     * @param id 订单 ID
     * @return 订单实体（含 items）
     */
    ReagentPurchaseOrder getDetail(Long id);
}
