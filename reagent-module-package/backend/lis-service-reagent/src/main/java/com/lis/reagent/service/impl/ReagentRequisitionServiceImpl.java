package com.lis.reagent.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.lis.common.core.exception.BusinessException;
import com.lis.reagent.dto.ApprovalDTO;
import com.lis.reagent.dto.RequisitionCreateDTO;
import com.lis.reagent.dto.StockOpContext;
import com.lis.reagent.entity.Reagent;
import com.lis.reagent.entity.ReagentRequisition;
import com.lis.reagent.entity.ReagentRequisitionItem;
import com.lis.reagent.entity.ReagentScrapDisposal;
import com.lis.reagent.entity.ReagentStock;
import com.lis.reagent.enums.Direction;
import com.lis.reagent.enums.ReqStatus;
import com.lis.reagent.mapper.ReagentMapper;
import com.lis.reagent.mapper.ReagentRequisitionItemMapper;
import com.lis.reagent.mapper.ReagentRequisitionMapper;
import com.lis.reagent.mapper.ReagentScrapDisposalMapper;
import com.lis.reagent.service.ReagentRequisitionService;
import com.lis.reagent.service.ReagentStockService;
import com.lis.reagent.support.BizNoGenerator;
import com.lis.reagent.support.ReagentWarehouseGuard;
import com.lis.reagent.support.RequisitionTypeSpec;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 统一出入库业务单服务实现（R-05）。
 *
 * <p>执行器只按 {@link RequisitionTypeSpec#getDirection()} 做 IN / OUT / MOVE 三分支，
 * 与类型数量解耦（设计文档 §2.3.1）。</p>
 *
 * @author LIS Team
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ReagentRequisitionServiceImpl
        extends ServiceImpl<ReagentRequisitionMapper, ReagentRequisition>
        implements ReagentRequisitionService {

    /** 业务单号前缀（R-05 统一单号：REQ + yyyyMMdd + 4位序号） */
    private static final String NO_PREFIX = "REQ";

    private final ReagentRequisitionMapper requisitionMapper;
    private final ReagentRequisitionItemMapper itemMapper;
    private final ReagentMapper reagentMapper;
    private final ReagentStockService stockService;
    private final ReagentScrapDisposalMapper scrapDisposalMapper;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ReagentRequisition create(RequisitionCreateDTO dto) {
        if (dto == null || dto.getReqType() == null) {
            throw new BusinessException("单据类型不能为空");
        }
        RequisitionTypeSpec spec = RequisitionTypeSpec.of(dto.getReqType());
        if (dto.getItems() == null || dto.getItems().isEmpty()) {
            throw new BusinessException("明细不能为空");
        }
        // 类型规约校验（报废原因 / 归还源单 / 借用归还日 / 调拨仓库对）
        if (spec.requiresScrapReason() && (dto.getReason() == null || dto.getReason().trim().isEmpty())) {
            throw new BusinessException("报废单必须填写报废原因");
        }
        if (spec.requiresSourceReq() && dto.getSourceReqId() == null) {
            throw new BusinessException("归还单必须关联原借用单");
        }
        if (spec.requiresExpectReturnDate() && dto.getExpectReturnDate() == null) {
            throw new BusinessException("借用单必须填写预计归还日期");
        }
        if (spec.isWarehousePairRequired() && (dto.getFromWarehouseId() == null || dto.getToWarehouseId() == null)) {
            throw new BusinessException("调拨单必须同时指定源仓库与目标仓库");
        }

        ReagentRequisition req;
        if (dto.getId() == null) {
            req = new ReagentRequisition();
            req.setReqNo(BizNoGenerator.nextNo(NO_PREFIX, requisitionMapper::selectMaxNo));
            req.setStatus(ReqStatus.DRAFT.getCode());
            req.setCreateTime(LocalDateTime.now());
        } else {
            req = requisitionMapper.selectByIdForUpdate(dto.getId());
            if (req == null) {
                throw new BusinessException("业务单不存在: " + dto.getId());
            }
            ReqStatus status = ReqStatus.of(req.getStatus());
            if (status != ReqStatus.DRAFT && status != ReqStatus.REJECTED) {
                throw new BusinessException("当前状态不允许修改明细: "
                        + (status == null ? req.getStatus() : status.getLabel()));
            }
            itemMapper.delete(new LambdaQueryWrapper<ReagentRequisitionItem>()
                    .eq(ReagentRequisitionItem::getReqId, req.getId()));
        }
        req.setReqType(dto.getReqType());
        req.setDeptId(dto.getDeptId());
        req.setDeptName(dto.getDeptName());
        req.setApplyUserId(dto.getApplyUserId());
        req.setApplyUserName(dto.getApplyUserName());
        req.setFromWarehouseId(dto.getFromWarehouseId());
        req.setToWarehouseId(dto.getToWarehouseId());
        req.setExpectReturnDate(dto.getExpectReturnDate());
        req.setSourceReqId(dto.getSourceReqId());
        req.setReason(dto.getReason());
        req.setRemark(dto.getRemark());
        req.setUpdateTime(LocalDateTime.now());
        this.saveOrUpdate(req);

        // 落明细：快照试剂信息 + 危险品标记
        BigDecimal total = BigDecimal.ZERO;
        int hazard = 0;
        List<ReagentRequisitionItem> items = new ArrayList<>();
        for (RequisitionCreateDTO.RequisitionItemDTO d : dto.getItems()) {
            if (d.getReagentId() == null) {
                throw new BusinessException("明细缺少试剂");
            }
            if (d.getQuantity() == null || d.getQuantity().compareTo(BigDecimal.ZERO) <= 0) {
                throw new BusinessException("明细数量必须大于 0");
            }
            if (spec.getDirection() == Direction.OUT && d.getStockId() == null) {
                throw new BusinessException("出库类明细必须指定批次（stockId）");
            }
            Reagent reagent = reagentMapper.selectById(d.getReagentId());
            if (reagent == null) {
                throw new BusinessException("试剂不存在: " + d.getReagentId());
            }
            ReagentRequisitionItem item = new ReagentRequisitionItem();
            item.setReqId(req.getId());
            item.setReagentId(d.getReagentId());
            item.setReagentCode(reagent.getReagentCode());
            item.setReagentName(reagent.getReagentName());
            item.setStockId(d.getStockId());
            item.setLotNo(d.getLotNo());
            item.setExpiryDate(d.getExpiryDate());
            item.setQuantity(d.getQuantity());
            item.setUnit(d.getUnit() != null ? d.getUnit() : reagent.getUnit());
            item.setUnitPrice(reagent.getPricePurchase());
            item.setAmount(reagent.getPricePurchase() == null ? BigDecimal.ZERO
                    : reagent.getPricePurchase().multiply(d.getQuantity()));
            item.setHazardFlag(reagent.getHazardFlag() == null ? 0 : reagent.getHazardFlag());
            item.setRemark(d.getRemark());
            item.setCreateTime(LocalDateTime.now());
            items.add(item);
            total = total.add(item.getAmount());
            if (item.getHazardFlag() == 1) {
                hazard = 1;
            }
        }
        for (ReagentRequisitionItem item : items) {
            itemMapper.insert(item);
        }
        req.setHazardFlag(hazard);
        req.setTotalAmount(total);
        requisitionMapper.updateById(req);
        log.info("创建业务单: reqNo={}, reqType={}, hazard={}, itemCount={}",
                req.getReqNo(), req.getReqType(), hazard, items.size());
        return req;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void submit(Long id, StockOpContext ctx) {
        ReagentRequisition req = requisitionMapper.selectByIdForUpdate(id);
        if (req == null) {
            throw new BusinessException("业务单不存在: " + id);
        }
        ReqStatus status = ReqStatus.of(req.getStatus());
        if (status != ReqStatus.DRAFT && status != ReqStatus.REJECTED) {
            throw new BusinessException("当前状态不允许提交: "
                    + (status == null ? req.getStatus() : status.getLabel()));
        }
        RequisitionTypeSpec spec = RequisitionTypeSpec.of(req.getReqType());
        // Q6 默认：普通试剂免审；危险品 / 全局开关强制审批
        boolean hazard = req.getHazardFlag() != null && req.getHazardFlag() == 1;
        boolean requiresApproval = spec.requiresApproval(hazard, false);
        req.setStatus(requiresApproval ? ReqStatus.PENDING.getCode() : ReqStatus.APPROVED.getCode());
        req.setUpdateTime(LocalDateTime.now());
        requisitionMapper.updateById(req);
        log.info("业务单提交: reqNo={}, requiresApproval={}", req.getReqNo(), requiresApproval);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void approve(Long id, ApprovalDTO dto) {
        ReagentRequisition req = requisitionMapper.selectByIdForUpdate(id);
        if (req == null) {
            throw new BusinessException("业务单不存在: " + id);
        }
        ReqStatus status = ReqStatus.of(req.getStatus());
        if (status != ReqStatus.PENDING) {
            throw new BusinessException("仅待审单据可审批通过: "
                    + (status == null ? req.getStatus() : status.getLabel()));
        }
        req.setStatus(ReqStatus.APPROVED.getCode());
        req.setApproverId(dto == null ? null : dto.getApproverId());
        req.setApproverName(dto == null ? null : dto.getApproverName());
        req.setApproveTime(LocalDateTime.now());
        req.setApproveOpinion(dto == null ? null : dto.getOpinion());
        req.setUpdateTime(LocalDateTime.now());
        requisitionMapper.updateById(req);
        log.info("业务单审批通过: reqNo={}", req.getReqNo());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void reject(Long id, ApprovalDTO dto) {
        ReagentRequisition req = requisitionMapper.selectByIdForUpdate(id);
        if (req == null) {
            throw new BusinessException("业务单不存在: " + id);
        }
        ReqStatus status = ReqStatus.of(req.getStatus());
        if (status != ReqStatus.PENDING) {
            throw new BusinessException("仅待审单据可驳回: "
                    + (status == null ? req.getStatus() : status.getLabel()));
        }
        req.setStatus(ReqStatus.REJECTED.getCode());
        req.setApproverId(dto == null ? null : dto.getApproverId());
        req.setApproverName(dto == null ? null : dto.getApproverName());
        req.setApproveTime(LocalDateTime.now());
        req.setApproveOpinion(dto == null ? null : dto.getOpinion());
        req.setUpdateTime(LocalDateTime.now());
        requisitionMapper.updateById(req);
        log.info("业务单审批驳回: reqNo={}", req.getReqNo());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void execute(Long id, StockOpContext ctx) {
        ReagentRequisition req = requisitionMapper.selectByIdForUpdate(id);
        if (req == null) {
            throw new BusinessException("业务单不存在: " + id);
        }
        ReqStatus status = ReqStatus.of(req.getStatus());
        if (status != ReqStatus.APPROVED) {
            throw new BusinessException("仅已审单据可执行: "
                    + (status == null ? req.getStatus() : status.getLabel()));
        }
        RequisitionTypeSpec spec = RequisitionTypeSpec.of(req.getReqType());
        // T-15 高危审批 + 环保处置：危险品报废单执行前必须已登记环保处置四要素
        if (spec == RequisitionTypeSpec.SCRAP
                && req.getHazardFlag() != null && req.getHazardFlag() == 1) {
            ReagentScrapDisposal disposal = scrapDisposalMapper.selectOne(
                    new LambdaQueryWrapper<ReagentScrapDisposal>()
                            .eq(ReagentScrapDisposal::getReqId, id));
            if (disposal == null
                    || !notBlank(disposal.getDisposalMethod())
                    || !notBlank(disposal.getDisposalOrg())
                    || !notBlank(disposal.getDisposalCert())
                    || disposal.getDisposalDate() == null) {
                throw new BusinessException("危险品报废单必须先登记环保处置信息");
            }
        }
        List<ReagentRequisitionItem> items = itemMapper.selectList(
                new LambdaQueryWrapper<ReagentRequisitionItem>()
                        .eq(ReagentRequisitionItem::getReqId, id));
        if (items.isEmpty()) {
            throw new BusinessException("业务单无明细，无法执行");
        }
        // 业务上下文：单号 + REQ 关联 + 科室（R-05 验收：流水 deptId 有值）
        StockOpContext execCtx = (ctx == null ? StockOpContext.builder().build() : ctx)
                .withSource(req.getReqNo(), "REQ", req.getId());
        for (ReagentRequisitionItem item : items) {
            executeItem(spec, item, req, execCtx);
        }
        req.setStatus(ReqStatus.EXECUTED.getCode());
        req.setExecutorId(execCtx.getOperatorId());
        req.setExecutorName(execCtx.getOperatorName());
        req.setExecuteTime(LocalDateTime.now());
        req.setUpdateTime(LocalDateTime.now());
        requisitionMapper.updateById(req);
        log.info("业务单执行完成: reqNo={}, type={}, itemCount={}",
                req.getReqNo(), spec.getLabel(), items.size());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void cancel(Long id, String operatorName) {
        ReagentRequisition req = requisitionMapper.selectByIdForUpdate(id);
        if (req == null) {
            throw new BusinessException("业务单不存在: " + id);
        }
        ReqStatus status = ReqStatus.of(req.getStatus());
        if (status == null || status.isTerminal()) {
            throw new BusinessException("当前状态不允许作废: "
                    + (status == null ? req.getStatus() : status.getLabel()));
        }
        req.setStatus(ReqStatus.CANCELLED.getCode());
        req.setUpdateTime(LocalDateTime.now());
        requisitionMapper.updateById(req);
        log.info("业务单作废: reqNo={}, operator={}", req.getReqNo(), operatorName);
    }

    @Override
    public Page<ReagentRequisition> queryPage(long current, long size, Integer reqType, Integer status,
                                              String reqNo, Long deptId) {
        LambdaQueryWrapper<ReagentRequisition> w = new LambdaQueryWrapper<>();
        if (reqType != null) {
            w.eq(ReagentRequisition::getReqType, reqType);
        }
        if (status != null) {
            w.eq(ReagentRequisition::getStatus, status);
        }
        if (reqNo != null && !reqNo.trim().isEmpty()) {
            w.like(ReagentRequisition::getReqNo, reqNo.trim());
        }
        if (deptId != null) {
            w.eq(ReagentRequisition::getDeptId, deptId);
        }
        w.orderByDesc(ReagentRequisition::getCreateTime);
        return this.page(new Page<>(current, size), w);
    }

    @Override
    public ReagentRequisition getDetail(Long id) {
        ReagentRequisition req = this.getById(id);
        if (req == null) {
            throw new BusinessException("业务单不存在: " + id);
        }
        req.setItems(itemMapper.selectList(new LambdaQueryWrapper<ReagentRequisitionItem>()
                .eq(ReagentRequisitionItem::getReqId, id)
                .orderByAsc(ReagentRequisitionItem::getId)));
        return req;
    }

    /**
     * 单条明细执行：按方向三分支分派（与类型数量解耦，永不增加分支）。
     */
    private void executeItem(RequisitionTypeSpec spec, ReagentRequisitionItem item,
                             ReagentRequisition req, StockOpContext ctx) {
        switch (spec.getDirection()) {
            case OUT -> stockService.outStockTyped(item.getStockId(), item.getQuantity(),
                    spec.getRecordType(), ctx);
            case IN -> stockService.inStockTyped(buildStock(item, req.getToWarehouseId()),
                    item.getQuantity(), spec.getRecordType(), ctx);
            case MOVE -> {
                // 调拨：一单两动作，同事务两条流水
                stockService.outStockTyped(item.getStockId(), item.getQuantity(),
                        spec.getRecordType(), ctx);
                stockService.inStockTyped(buildStock(item, req.getToWarehouseId()),
                        item.getQuantity(), spec.getRecordType(), ctx);
            }
            default -> throw new BusinessException("未知的出入方向: " + spec.getDirection());
        }
    }

    /**
     * 入库类（退库 / 归还 / 调拨目标）由明细构建库存批次。
     */
    private ReagentStock buildStock(ReagentRequisitionItem item, Long warehouseId) {
        ReagentStock stock = new ReagentStock();
        stock.setReagentId(item.getReagentId());
        stock.setLotNo(item.getLotNo());
        stock.setExpiryDate(item.getExpiryDate());
        // 仓库 ID 归一化：null → 0（默认库房，禁 NULL 进 wrapper）
        stock.setWarehouseId(ReagentWarehouseGuard.normalize(warehouseId));
        return stock;
    }

    /** 非空非空白串判断。 */
    private static boolean notBlank(String s) {
        return s != null && !s.trim().isEmpty();
    }
}
