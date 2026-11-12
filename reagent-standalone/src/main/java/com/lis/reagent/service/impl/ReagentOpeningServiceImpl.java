package com.lis.reagent.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.lis.common.core.exception.BusinessException;
import com.lis.reagent.entity.Reagent;
import com.lis.reagent.entity.ReagentOpening;
import com.lis.reagent.entity.ReagentStock;
import com.lis.reagent.mapper.ReagentMapper;
import com.lis.reagent.mapper.ReagentOpeningMapper;
import com.lis.reagent.mapper.ReagentStockMapper;
import com.lis.reagent.service.ReagentOpeningService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class ReagentOpeningServiceImpl extends ServiceImpl<ReagentOpeningMapper, ReagentOpening>
        implements ReagentOpeningService {

    private final ReagentMapper reagentMapper;
    private final ReagentStockMapper reagentStockMapper;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ReagentOpening createOpening(ReagentOpening opening) {
        if (opening.getOpenTime() == null) {
            opening.setOpenTime(LocalDateTime.now());
        }
        Reagent reagent = reagentMapper.selectById(opening.getReagentId());
        if (reagent != null) {
            opening.setReagentName(reagent.getReagentName());
            if (reagent.getOpenedExpiryDays() != null) {
                opening.setOpenedExpiry(LocalDate.now().plusDays(reagent.getOpenedExpiryDays()));
            }
        }
        opening.setStatus(1);
        // 默认按首次开封登记，未闭封
        if (opening.getOpeningType() == null) {
            opening.setOpeningType(1);
        }
        if (opening.getSealStatus() == null) {
            opening.setSealStatus(0);
        }
        opening.setCreateTime(LocalDateTime.now());
        this.save(opening);
        log.info("试剂开瓶登记: reagentId={}, stockId={}, openedExpiry={}",
                opening.getReagentId(), opening.getStockId(), opening.getOpenedExpiry());
        return opening;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int lockExpiredOpenings() {
        List<ReagentOpening> expired = this.list(new LambdaQueryWrapper<ReagentOpening>()
                .eq(ReagentOpening::getStatus, 1)
                .le(ReagentOpening::getOpenedExpiry, LocalDate.now()));
        int count = 0;
        for (ReagentOpening o : expired) {
            o.setStatus(0);
            this.updateById(o);
            count++;
        }
        if (count > 0) {
            log.info("锁定开瓶过期记录 {} 个", count);
        }
        return count;
    }

    @Override
    public Page<ReagentOpening> queryPage(long current, long size, Long reagentId, Integer status,
                                          Integer openingType, LocalDate startDate, LocalDate endDate) {
        LambdaQueryWrapper<ReagentOpening> wrapper = new LambdaQueryWrapper<>();
        if (reagentId != null) {
            wrapper.eq(ReagentOpening::getReagentId, reagentId);
        }
        if (status != null) {
            wrapper.eq(ReagentOpening::getStatus, status);
        }
        if (openingType != null) {
            wrapper.eq(ReagentOpening::getOpeningType, openingType);
        }
        // 日期区间按开瓶时间过滤，endDate 当天含全天
        if (startDate != null) {
            wrapper.ge(ReagentOpening::getOpenTime, startDate.atStartOfDay());
        }
        if (endDate != null) {
            wrapper.lt(ReagentOpening::getOpenTime, endDate.plusDays(1).atStartOfDay());
        }
        wrapper.orderByDesc(ReagentOpening::getOpenTime);
        Page<ReagentOpening> page = this.page(new Page<>(current, size), wrapper);
        for (ReagentOpening o : page.getRecords()) {
            Reagent reagent = reagentMapper.selectById(o.getReagentId());
            if (reagent != null) {
                o.setReagentName(reagent.getReagentName());
            }
        }
        return page;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ReagentOpening reopen(Long sourceId, Long operatorId, String operatorName) {
        ReagentOpening source = this.getById(sourceId);
        if (source == null) {
            throw new BusinessException("开封记录不存在");
        }
        // 闭封后状态为 2，故二次开封允许来源记录为正常(1)或已闭封(2)，排除过期/耗尽
        if (!Integer.valueOf(1).equals(source.getStatus()) && !Integer.valueOf(2).equals(source.getStatus())) {
            throw new BusinessException("仅正常或已闭封状态的开瓶记录可二次开封");
        }
        // 首次开封（opening_type=1）或已闭封（seal_status=1）的记录可二次开封
        boolean firstOpening = Integer.valueOf(1).equals(source.getOpeningType());
        boolean sealed = Integer.valueOf(1).equals(source.getSealStatus());
        if (!firstOpening && !sealed) {
            throw new BusinessException("仅已闭封或首次开封的记录可二次开封");
        }

        ReagentOpening opening = new ReagentOpening();
        opening.setReagentId(source.getReagentId());
        opening.setStockId(source.getStockId());
        opening.setLotNo(source.getLotNo());
        opening.setOpenTime(LocalDateTime.now());
        opening.setStatus(1);
        opening.setOperatorId(operatorId);
        opening.setOperatorName(operatorName);
        opening.setRemark("二次开封，来源记录ID=" + sourceId);
        opening.setOpeningType(2);
        opening.setParentId(sourceId);
        opening.setSealStatus(0);
        opening.setCreateTime(LocalDateTime.now());
        fillReagentInfo(opening);
        this.save(opening);
        // 来源记录保持原状，仅新增二次开封记录
        log.info("试剂二次开封: sourceId={}, newId={}, operator={}", sourceId, opening.getId(), operatorName);
        return opening;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void seal(Long id, String operatorName) {
        ReagentOpening opening = this.getById(id);
        if (opening == null) {
            throw new BusinessException("开封记录不存在");
        }
        if (!Integer.valueOf(1).equals(opening.getStatus())) {
            throw new BusinessException("仅正常状态的开瓶记录可闭封");
        }
        checkHazardApproved(opening, "闭封");

        opening.setSealStatus(1);
        opening.setSealTime(LocalDateTime.now());
        opening.setSealOperator(operatorName);
        opening.setStatus(2);
        this.updateById(opening);
        log.info("试剂开封闭封: id={}, operator={}", id, operatorName);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void exhaust(Long id) {
        ReagentOpening opening = this.getById(id);
        if (opening == null) {
            throw new BusinessException("开封记录不存在");
        }
        if (!Integer.valueOf(1).equals(opening.getStatus()) && !Integer.valueOf(2).equals(opening.getStatus())) {
            throw new BusinessException("仅正常或已闭封状态的开瓶记录可耗尽");
        }
        checkHazardApproved(opening, "耗尽");

        opening.setExhaustTime(LocalDateTime.now());
        opening.setStatus(3);
        this.updateById(opening);
        log.info("试剂开封耗尽: id={}", id);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public List<ReagentOpening> subPackage(Long stockId, Long reagentId, Integer packCount,
                                           Long operatorId, String operatorName) {
        if (stockId == null) {
            throw new BusinessException("库存批次ID不能为空");
        }
        if (packCount == null || packCount < 1) {
            throw new BusinessException("分包数量必须大于等于1");
        }
        ReagentStock stock = reagentStockMapper.selectById(stockId);
        if (stock == null) {
            throw new BusinessException("库存批次不存在");
        }
        Long actualReagentId = reagentId != null ? reagentId : stock.getReagentId();
        Reagent reagent = reagentMapper.selectById(actualReagentId);
        if (reagent == null) {
            throw new BusinessException("试剂不存在");
        }

        // 分包份数 = packCount + 1（含原包装一份）
        int packTotal = packCount + 1;
        List<ReagentOpening> result = new ArrayList<>(packTotal);
        ReagentOpening first = null;
        for (int i = 1; i <= packTotal; i++) {
            ReagentOpening opening = new ReagentOpening();
            opening.setReagentId(actualReagentId);
            opening.setReagentName(reagent.getReagentName());
            opening.setStockId(stockId);
            opening.setLotNo(stock.getLotNo());
            opening.setOpenTime(LocalDateTime.now());
            opening.setStatus(1);
            opening.setOperatorId(operatorId);
            opening.setOperatorName(operatorName);
            opening.setOpeningType(3);
            opening.setPackNo(i);
            opening.setPackTotal(packTotal);
            opening.setSealStatus(0);
            opening.setCreateTime(LocalDateTime.now());
            opening.setRemark("质控品分包 " + i + "/" + packTotal);
            if (reagent.getOpenedExpiryDays() != null) {
                opening.setOpenedExpiry(LocalDate.now().plusDays(reagent.getOpenedExpiryDays()));
            }
            if (first != null) {
                // 除第一条外，其余记录指向第一条
                opening.setParentId(first.getId());
            }
            this.save(opening);
            if (first == null) {
                first = opening;
            }
            result.add(opening);
        }
        log.info("质控品分包: stockId={}, reagentId={}, packCount={}, packTotal={}",
                stockId, actualReagentId, packCount, packTotal);
        return result;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ReagentOpening applyHazardOpening(Long reagentId, Long stockId, BigDecimal demandQty,
                                             Long operatorId, String operatorName, String remark) {
        if (reagentId == null) {
            throw new BusinessException("试剂ID不能为空");
        }
        Reagent reagent = reagentMapper.selectById(reagentId);
        if (reagent == null) {
            throw new BusinessException("试剂不存在");
        }
        if (!Integer.valueOf(1).equals(reagent.getHazardFlag())) {
            throw new BusinessException("该试剂不是危险品，不能申请危化品开封");
        }
        if (demandQty == null || demandQty.compareTo(BigDecimal.ZERO) <= 0) {
            throw new BusinessException("需求数量必须大于0");
        }

        ReagentOpening opening = new ReagentOpening();
        opening.setReagentId(reagentId);
        opening.setReagentName(reagent.getReagentName());
        if (stockId != null) {
            ReagentStock stock = reagentStockMapper.selectById(stockId);
            if (stock == null) {
                throw new BusinessException("库存批次不存在");
            }
            opening.setStockId(stockId);
            opening.setLotNo(stock.getLotNo());
        }
        opening.setOpenTime(LocalDateTime.now());
        opening.setStatus(1);
        opening.setOperatorId(operatorId);
        opening.setOperatorName(operatorName);
        opening.setRemark(remark);
        opening.setOpeningType(4);
        opening.setDemandQty(demandQty);
        opening.setApprovalStatus(0);
        opening.setSealStatus(0);
        opening.setCreateTime(LocalDateTime.now());
        if (reagent.getOpenedExpiryDays() != null) {
            opening.setOpenedExpiry(LocalDate.now().plusDays(reagent.getOpenedExpiryDays()));
        }
        this.save(opening);
        log.info("危化品开封申请: reagentId={}, stockId={}, demandQty={}, operator={}",
                reagentId, stockId, demandQty, operatorName);
        return opening;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void approveHazard(Long id, Long approverId, String approverName, String opinion) {
        ReagentOpening opening = requireHazardPending(id);
        opening.setApprovalStatus(1);
        opening.setApproverId(approverId);
        opening.setApproverName(approverName);
        opening.setApproveTime(LocalDateTime.now());
        opening.setApproveOpinion(opinion);
        this.updateById(opening);
        log.info("危化品开封审批通过: id={}, approver={}", id, approverName);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void rejectHazard(Long id, Long approverId, String approverName, String opinion) {
        ReagentOpening opening = requireHazardPending(id);
        opening.setApprovalStatus(2);
        opening.setApproverId(approverId);
        opening.setApproverName(approverName);
        opening.setApproveTime(LocalDateTime.now());
        opening.setApproveOpinion(opinion);
        this.updateById(opening);
        log.info("危化品开封审批驳回: id={}, approver={}", id, approverName);
    }

    /** 校验危化品记录是否已审批通过，未通过不允许后续闭封/耗尽 */
    private void checkHazardApproved(ReagentOpening opening, String action) {
        if (Integer.valueOf(4).equals(opening.getOpeningType())
                && !Integer.valueOf(1).equals(opening.getApprovalStatus())) {
            throw new BusinessException("危化品开封记录未审批通过，不允许" + action);
        }
    }

    /** 校验危化品待审批记录：必须是危化品申请且尚未审批 */
    private ReagentOpening requireHazardPending(Long id) {
        ReagentOpening opening = this.getById(id);
        if (opening == null) {
            throw new BusinessException("危化品开封记录不存在");
        }
        if (!Integer.valueOf(4).equals(opening.getOpeningType())) {
            throw new BusinessException("非危化品开封申请记录");
        }
        if (!Integer.valueOf(0).equals(opening.getApprovalStatus())) {
            throw new BusinessException("该危化品开封申请已审批，不能重复审批");
        }
        return opening;
    }

    /** 按试剂开瓶效期填充试剂名称与开瓶效期 */
    private void fillReagentInfo(ReagentOpening opening) {
        Reagent reagent = reagentMapper.selectById(opening.getReagentId());
        if (reagent != null) {
            opening.setReagentName(reagent.getReagentName());
            if (reagent.getOpenedExpiryDays() != null) {
                opening.setOpenedExpiry(LocalDate.now().plusDays(reagent.getOpenedExpiryDays()));
            }
        }
    }
}
