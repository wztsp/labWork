package com.lis.reagent.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.lis.common.core.exception.BusinessException;
import com.lis.reagent.dto.OrderCreateDTO;
import com.lis.reagent.dto.PurchaseOrderMergeDTO;
import com.lis.reagent.dto.StockOpContext;
import com.lis.reagent.entity.Reagent;
import com.lis.reagent.entity.ReagentPurchaseApply;
import com.lis.reagent.entity.ReagentPurchaseApplyItem;
import com.lis.reagent.entity.ReagentPurchaseOrder;
import com.lis.reagent.entity.ReagentPurchaseOrderItem;
import com.lis.reagent.enums.ApplyStatus;
import com.lis.reagent.enums.OrderStatus;
import com.lis.reagent.mapper.ReagentMapper;
import com.lis.reagent.mapper.ReagentPurchaseApplyItemMapper;
import com.lis.reagent.mapper.ReagentPurchaseApplyMapper;
import com.lis.reagent.mapper.ReagentPurchaseOrderItemMapper;
import com.lis.reagent.mapper.ReagentPurchaseOrderMapper;
import com.lis.reagent.mapper.SupplierMapper;
import com.lis.reagent.service.ReagentBudgetService;
import com.lis.reagent.service.ReagentPurchaseOrderService;
import com.lis.reagent.service.SysConfigService;
import com.lis.reagent.support.BizNoGenerator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * 采购订单服务实现。
 *
 * @author LIS Team
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ReagentPurchaseOrderServiceImpl
        extends ServiceImpl<ReagentPurchaseOrderMapper, ReagentPurchaseOrder>
        implements ReagentPurchaseOrderService {

    /** 订单号前缀（R-05 统一单号：PUR + yyyyMMdd + 4位序号） */
    private static final String NO_PREFIX = "PUR";

    private final ReagentPurchaseOrderMapper orderMapper;
    private final ReagentPurchaseOrderItemMapper orderItemMapper;
    private final ReagentPurchaseApplyMapper applyMapper;
    private final ReagentPurchaseApplyItemMapper applyItemMapper;
    private final ReagentMapper reagentMapper;
    private final SupplierMapper supplierMapper;
    private final ReagentBudgetService budgetService;
    private final SysConfigService sysConfigService;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ReagentPurchaseOrder createFromApply(OrderCreateDTO dto) {
        if (dto == null || dto.getApplyId() == null) {
            throw new BusinessException("来源采购申请不能为空");
        }
        if (dto.getSupplierId() == null) {
            throw new BusinessException("请选择供应商");
        }
        ReagentPurchaseApply apply = applyMapper.selectById(dto.getApplyId());
        if (apply == null) {
            throw new BusinessException("采购申请不存在: " + dto.getApplyId());
        }
        if (ApplyStatus.of(apply.getStatus()) != ApplyStatus.APPROVED) {
            throw new BusinessException("仅已批准的采购申请可生成订单，当前状态: "
                    + (ApplyStatus.of(apply.getStatus()) == null ? apply.getStatus()
                    : ApplyStatus.of(apply.getStatus()).getLabel()));
        }
        if (orderMapper.countUnfinishedByApplyId(apply.getId()) > 0) {
            throw new BusinessException("该申请已有未完结订单，请勿重复生成");
        }
        if (supplierMapper.selectById(dto.getSupplierId()) == null) {
            throw new BusinessException("供应商不存在: " + dto.getSupplierId());
        }

        ReagentPurchaseOrder order = new ReagentPurchaseOrder();
        order.setOrderNo(BizNoGenerator.nextNo(NO_PREFIX, orderMapper::selectMaxNo));
        order.setApplyId(apply.getId());
        order.setApplyNo(apply.getApplyNo());
        order.setSupplierId(dto.getSupplierId());
        order.setSupplierName(dto.getSupplierName() != null ? dto.getSupplierName()
                : supplierMapper.selectById(dto.getSupplierId()).getSupplierName());
        order.setDeptId(dto.getDeptId() != null ? dto.getDeptId() : apply.getApplyDeptId());
        order.setDeptName(dto.getDeptName() != null ? dto.getDeptName() : apply.getApplyDeptName());
        order.setExpectDate(dto.getExpectDate());
        order.setOrderDate(dto.getOrderDate() != null ? dto.getOrderDate() : LocalDate.now());
        order.setStatus(OrderStatus.PENDING_ORDER.getCode());
        order.setApprovalStatus(0);
        order.setAuditStatus(0);
        order.setOperatorId(dto.getOperatorId());
        order.setOperatorName(dto.getOperatorName());
        order.setRemark(dto.getRemark());
        order.setCreateTime(LocalDateTime.now());
        order.setUpdateTime(LocalDateTime.now());
        this.save(order);

        // 明细：默认整单透传申请明细，单价取成交价（未填则取申请预估单价）
        List<ReagentPurchaseOrderItem> orderItems = buildOrderItems(dto, order.getId());
        BigDecimal total = BigDecimal.ZERO;
        for (ReagentPurchaseOrderItem item : orderItems) {
            total = total.add(item.getAmount());
            orderItemMapper.insert(item);
        }
        order.setTotalAmount(total);
        orderMapper.updateById(order);
        order.setItems(orderItems);
        log.info("创建采购订单: orderNo={}, applyNo={}, supplierId={}, total={}",
                order.getOrderNo(), order.getApplyNo(), order.getSupplierId(), total);
        return order;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ReagentPurchaseOrder merge(PurchaseOrderMergeDTO dto) {
        if (dto == null || dto.getApplyIds() == null || dto.getApplyIds().isEmpty()) {
            throw new BusinessException("待合并的采购申请不能为空");
        }
        if (dto.getSupplierId() == null) {
            throw new BusinessException("请选择供应商");
        }
        if (supplierMapper.selectById(dto.getSupplierId()) == null) {
            throw new BusinessException("供应商不存在: " + dto.getSupplierId());
        }
        // 申请 ID 去重，顺序保留；合并单明细按申请顺序逐条写入
        Set<Long> applyIds = new LinkedHashSet<>(dto.getApplyIds());
        applyIds.remove(null);
        if (applyIds.isEmpty()) {
            throw new BusinessException("待合并的采购申请不能为空");
        }

        List<ReagentPurchaseApply> applies = new ArrayList<>();
        for (Long applyId : applyIds) {
            ReagentPurchaseApply apply = applyMapper.selectById(applyId);
            if (apply == null) {
                throw new BusinessException("采购申请不存在: " + applyId);
            }
            if (ApplyStatus.of(apply.getStatus()) != ApplyStatus.APPROVED) {
                throw new BusinessException("仅已批准的采购申请可合并生成订单，申请号: " + apply.getApplyNo());
            }
            if (orderMapper.countUnfinishedByApplyId(apply.getId()) > 0) {
                throw new BusinessException("申请已有未完结订单，请勿重复生成: " + apply.getApplyNo());
            }
            applies.add(apply);
        }

        ReagentPurchaseApply first = applies.get(0);
        ReagentPurchaseOrder order = new ReagentPurchaseOrder();
        order.setOrderNo(BizNoGenerator.nextNo(NO_PREFIX, orderMapper::selectMaxNo));
        // 合并单 applyId 取第一张，完整清单落 applyIds（逗号分隔）
        order.setApplyId(first.getId());
        order.setApplyNo(first.getApplyNo());
        order.setApplyIds(applyIds.stream().map(String::valueOf)
                .collect(java.util.stream.Collectors.joining(",")));
        order.setSupplierId(dto.getSupplierId());
        order.setSupplierName(dto.getSupplierName() != null ? dto.getSupplierName()
                : supplierMapper.selectById(dto.getSupplierId()).getSupplierName());
        order.setDeptId(first.getApplyDeptId());
        order.setDeptName(first.getApplyDeptName());
        order.setExpectDate(dto.getExpectDate());
        order.setOrderDate(LocalDate.now());
        order.setStatus(OrderStatus.PENDING_ORDER.getCode());
        order.setApprovalStatus(0);
        order.setAuditStatus(0);
        order.setOperatorId(dto.getOperatorId());
        order.setOperatorName(dto.getOperatorName());
        order.setRemark(dto.getRemark());
        order.setCreateTime(LocalDateTime.now());
        order.setUpdateTime(LocalDateTime.now());
        this.save(order);

        // 明细逐条透传，执行供应商一致性校验（试剂主数据供应商非空时必须与所选供应商一致）
        List<ReagentPurchaseOrderItem> orderItems = new ArrayList<>();
        BigDecimal total = BigDecimal.ZERO;
        for (ReagentPurchaseApply apply : applies) {
            List<ReagentPurchaseApplyItem> applyItems = applyItemMapper.selectList(
                    new LambdaQueryWrapper<ReagentPurchaseApplyItem>()
                            .eq(ReagentPurchaseApplyItem::getApplyId, apply.getId()));
            if (applyItems.isEmpty()) {
                throw new BusinessException("来源申请无明细，无法生成订单: " + apply.getApplyNo());
            }
            for (ReagentPurchaseApplyItem ai : applyItems) {
                Reagent reagent = reagentMapper.selectById(ai.getReagentId());
                if (reagent != null && reagent.getSupplierId() != null
                        && !reagent.getSupplierId().equals(dto.getSupplierId())) {
                    throw new BusinessException("申请单 " + apply.getApplyNo() + " 中试剂["
                            + ai.getReagentCode() + "]的供应商与所选供应商不一致");
                }
                BigDecimal unitPrice = ai.getEstimatedPrice() == null ? BigDecimal.ZERO : ai.getEstimatedPrice();
                ReagentPurchaseOrderItem oi = new ReagentPurchaseOrderItem();
                oi.setOrderId(order.getId());
                oi.setApplyItemId(ai.getId());
                oi.setReagentId(ai.getReagentId());
                oi.setReagentCode(ai.getReagentCode());
                oi.setReagentName(ai.getReagentName());
                oi.setSpecification(ai.getSpecification());
                oi.setQuantity(ai.getQuantity());
                oi.setArrivedQty(BigDecimal.ZERO);
                oi.setUnit(ai.getUnit());
                oi.setUnitPrice(unitPrice);
                oi.setAmount(unitPrice.multiply(ai.getQuantity()));
                oi.setRemark(ai.getRemark());
                oi.setCreateTime(LocalDateTime.now());
                orderItems.add(oi);
                total = total.add(oi.getAmount());
            }
        }
        for (ReagentPurchaseOrderItem item : orderItems) {
            orderItemMapper.insert(item);
        }
        order.setTotalAmount(total);
        orderMapper.updateById(order);
        order.setItems(orderItems);
        log.info("合并采购订单: orderNo={}, applyIds={}, supplierId={}, itemCount={}, total={}",
                order.getOrderNo(), order.getApplyIds(), order.getSupplierId(), orderItems.size(), total);
        return order;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void approve(Long id, String opinion) {
        ReagentPurchaseOrder order = orderMapper.selectByIdForUpdate(id);
        if (order == null) {
            throw new BusinessException("采购订单不存在: " + id);
        }
        if (order.getApprovalStatus() != null && order.getApprovalStatus() != 0) {
            throw new BusinessException("仅待审订单可审批通过，当前审批状态: " + order.getApprovalStatus());
        }
        order.setApprovalStatus(1);
        order.setApprovalTime(LocalDateTime.now());
        order.setApprovalOpinion(opinion);
        order.setUpdateTime(LocalDateTime.now());
        orderMapper.updateById(order);
        log.info("采购订单审批通过: orderNo={}", order.getOrderNo());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void reject(Long id, String opinion) {
        ReagentPurchaseOrder order = orderMapper.selectByIdForUpdate(id);
        if (order == null) {
            throw new BusinessException("采购订单不存在: " + id);
        }
        if (order.getApprovalStatus() != null && order.getApprovalStatus() != 0) {
            throw new BusinessException("仅待审订单可驳回，当前审批状态: " + order.getApprovalStatus());
        }
        order.setApprovalStatus(2);
        order.setApprovalTime(LocalDateTime.now());
        order.setApprovalOpinion(opinion);
        order.setUpdateTime(LocalDateTime.now());
        orderMapper.updateById(order);
        log.info("采购订单审批驳回: orderNo={}, opinion={}", order.getOrderNo(), opinion);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void audit(Long id, Long auditorId, String auditorName) {
        ReagentPurchaseOrder order = orderMapper.selectByIdForUpdate(id);
        if (order == null) {
            throw new BusinessException("采购订单不存在: " + id);
        }
        if (order.getAuditStatus() != null && order.getAuditStatus() == 1) {
            throw new BusinessException("订单已审核，请勿重复审核");
        }
        if (order.getApprovalStatus() != null && order.getApprovalStatus() == 2) {
            throw new BusinessException("订单已审批驳回，不允许审核");
        }
        // 系统参数 reagent.purchase_order_need_approval：开启时订单必须审批通过才能审核
        boolean needApproval = sysConfigService.getBoolean("reagent.purchase_order_need_approval", false);
        if (needApproval && (order.getApprovalStatus() == null || order.getApprovalStatus() != 1)) {
            throw new BusinessException("订单尚未审批通过，不允许审核");
        }
        order.setAuditStatus(1);
        order.setAuditorId(auditorId);
        order.setAuditorName(auditorName);
        order.setAuditTime(LocalDateTime.now());
        order.setUpdateTime(LocalDateTime.now());
        orderMapper.updateById(order);
        log.info("采购订单审核: orderNo={}, auditor={}", order.getOrderNo(), auditorName);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void unaudit(Long id, Long auditorId, String auditorName) {
        ReagentPurchaseOrder order = orderMapper.selectByIdForUpdate(id);
        if (order == null) {
            throw new BusinessException("采购订单不存在: " + id);
        }
        if (order.getAuditStatus() == null || order.getAuditStatus() != 1) {
            throw new BusinessException("仅已审核订单可取消审核");
        }
        order.setAuditStatus(2);
        order.setAuditorId(auditorId);
        order.setAuditorName(auditorName);
        order.setAuditTime(LocalDateTime.now());
        order.setUpdateTime(LocalDateTime.now());
        orderMapper.updateById(order);
        log.info("采购订单取消审核: orderNo={}, operator={}", order.getOrderNo(), auditorName);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void placeOrder(Long id, StockOpContext ctx) {
        ReagentPurchaseOrder order = orderMapper.selectByIdForUpdate(id);
        if (order == null) {
            throw new BusinessException("采购订单不存在: " + id);
        }
        OrderStatus status = OrderStatus.of(order.getStatus());
        if (status != OrderStatus.PENDING_ORDER) {
            throw new BusinessException("仅待下单的订单可下达，当前状态: "
                    + (status == null ? order.getStatus() : status.getLabel()));
        }
        order.setStatus(OrderStatus.PLACED.getCode());
        order.setOrderDate(LocalDate.now());
        if (ctx != null && ctx.getOperatorId() != null) {
            order.setOperatorId(ctx.getOperatorId());
            order.setOperatorName(ctx.getOperatorName());
        }
        order.setUpdateTime(LocalDateTime.now());
        orderMapper.updateById(order);
        log.info("采购订单下单: orderNo={}", order.getOrderNo());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void cancel(Long id, StockOpContext ctx) {
        ReagentPurchaseOrder order = orderMapper.selectByIdForUpdate(id);
        if (order == null) {
            throw new BusinessException("采购订单不存在: " + id);
        }
        OrderStatus status = OrderStatus.of(order.getStatus());
        if (status == OrderStatus.ARRIVED || status == OrderStatus.CANCELLED) {
            throw new BusinessException("已到货 / 已取消的订单不可取消");
        }
        order.setStatus(OrderStatus.CANCELLED.getCode());
        order.setUpdateTime(LocalDateTime.now());
        orderMapper.updateById(order);
        // 取消释放预算占用（订单取消 → 采购未发生 → 释放申请时冻结的额度）
        ReagentPurchaseApply apply = applyMapper.selectById(order.getApplyId());
        if (apply != null) {
            budgetService.releaseForApply(apply.getBudgetId(), order.getTotalAmount() == null
                            ? apply.getTotalAmount() : order.getTotalAmount(),
                    apply.getId(), apply.getApplyNo(), ctx);
        }
        log.info("采购订单取消: orderNo={}", order.getOrderNo());
    }

    @Override
    public Page<ReagentPurchaseOrder> queryPage(long current, long size, String orderNo, Integer status,
                                                Long applyId, Long supplierId) {
        LambdaQueryWrapper<ReagentPurchaseOrder> w = new LambdaQueryWrapper<>();
        if (orderNo != null && !orderNo.trim().isEmpty()) {
            w.like(ReagentPurchaseOrder::getOrderNo, orderNo.trim());
        }
        if (status != null) {
            w.eq(ReagentPurchaseOrder::getStatus, status);
        }
        if (applyId != null) {
            w.eq(ReagentPurchaseOrder::getApplyId, applyId);
        }
        if (supplierId != null) {
            w.eq(ReagentPurchaseOrder::getSupplierId, supplierId);
        }
        w.orderByDesc(ReagentPurchaseOrder::getCreateTime);
        return this.page(new Page<>(current, size), w);
    }

    @Override
    public ReagentPurchaseOrder getDetail(Long id) {
        ReagentPurchaseOrder order = this.getById(id);
        if (order == null) {
            throw new BusinessException("采购订单不存在: " + id);
        }
        order.setItems(orderItemMapper.selectByOrderId(id));
        return order;
    }

    /**
     * 由申请明细构建订单明细（默认整单透传，数量不得超过申请量）。
     */
    private List<ReagentPurchaseOrderItem> buildOrderItems(OrderCreateDTO dto, Long orderId) {
        List<ReagentPurchaseApplyItem> applyItems = applyItemMapper.selectList(
                new LambdaQueryWrapper<ReagentPurchaseApplyItem>()
                        .eq(ReagentPurchaseApplyItem::getApplyId, dto.getApplyId()));
        if (applyItems.isEmpty()) {
            throw new BusinessException("来源申请无明细，无法生成订单");
        }
        List<ReagentPurchaseOrderItem> result = new ArrayList<>();
        if (dto.getItems() == null || dto.getItems().isEmpty()) {
            // 整单透传
            for (ReagentPurchaseApplyItem ai : applyItems) {
                BigDecimal unitPrice = ai.getEstimatedPrice() == null ? BigDecimal.ZERO : ai.getEstimatedPrice();
                ReagentPurchaseOrderItem oi = new ReagentPurchaseOrderItem();
                oi.setOrderId(orderId);
                oi.setApplyItemId(ai.getId());
                oi.setReagentId(ai.getReagentId());
                oi.setReagentCode(ai.getReagentCode());
                oi.setReagentName(ai.getReagentName());
                oi.setSpecification(ai.getSpecification());
                oi.setQuantity(ai.getQuantity());
                oi.setArrivedQty(BigDecimal.ZERO);
                oi.setUnit(ai.getUnit());
                oi.setUnitPrice(unitPrice);
                oi.setAmount(unitPrice.multiply(ai.getQuantity()));
                oi.setRemark(ai.getRemark());
                oi.setCreateTime(LocalDateTime.now());
                result.add(oi);
            }
            return result;
        }
        // 裁剪明细：按 dto.items 生成
        for (OrderCreateDTO.OrderItemDTO d : dto.getItems()) {
            if (d.getReagentId() == null || d.getQuantity() == null
                    || d.getQuantity().compareTo(BigDecimal.ZERO) <= 0) {
                throw new BusinessException("订单明细缺少试剂或数量无效");
            }
            BigDecimal unitPrice = d.getUnitPrice() == null ? BigDecimal.ZERO : d.getUnitPrice();
            Reagent reagent = reagentMapper.selectById(d.getReagentId());
            ReagentPurchaseOrderItem oi = new ReagentPurchaseOrderItem();
            oi.setOrderId(orderId);
            oi.setApplyItemId(d.getApplyItemId());
            oi.setReagentId(d.getReagentId());
            if (reagent != null) {
                oi.setReagentCode(reagent.getReagentCode());
                oi.setReagentName(reagent.getReagentName());
                oi.setSpecification(reagent.getSpecification());
            }
            oi.setQuantity(d.getQuantity());
            oi.setArrivedQty(BigDecimal.ZERO);
            oi.setUnit(d.getUnit());
            oi.setUnitPrice(unitPrice);
            oi.setAmount(unitPrice.multiply(d.getQuantity()));
            oi.setRemark(d.getRemark());
            oi.setCreateTime(LocalDateTime.now());
            result.add(oi);
        }
        return result;
    }
}
