package com.lis.reagent.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.lis.common.core.exception.BusinessException;
import com.lis.common.core.result.PageResult;
import com.lis.reagent.entity.ReagentInvoice;
import com.lis.reagent.mapper.ReagentInvoiceMapper;
import com.lis.reagent.service.ReagentInvoiceService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 供应商发票服务实现。
 */
@Service
@RequiredArgsConstructor
public class ReagentInvoiceServiceImpl implements ReagentInvoiceService {

    private final ReagentInvoiceMapper invoiceMapper;

    @Override
    public PageResult<ReagentInvoice> page(long current, long size, String invoiceNo, Long supplierId,
                                           LocalDate startDate, LocalDate endDate) {
        LambdaQueryWrapper<ReagentInvoice> wrapper = new LambdaQueryWrapper<>();
        if (invoiceNo != null && !invoiceNo.trim().isEmpty()) {
            wrapper.like(ReagentInvoice::getInvoiceNo, invoiceNo.trim());
        }
        if (supplierId != null) {
            wrapper.eq(ReagentInvoice::getSupplierId, supplierId);
        }
        if (startDate != null) {
            wrapper.ge(ReagentInvoice::getInvoiceDate, startDate);
        }
        if (endDate != null) {
            wrapper.le(ReagentInvoice::getInvoiceDate, endDate);
        }
        wrapper.orderByDesc(ReagentInvoice::getInvoiceDate)
                .orderByDesc(ReagentInvoice::getId);
        Page<ReagentInvoice> page = invoiceMapper.selectPage(new Page<>(current, size), wrapper);
        return new PageResult<>(page.getRecords(), page.getTotal(), page.getCurrent(), page.getSize());
    }

    @Override
    public ReagentInvoice register(ReagentInvoice invoice) {
        if (invoice == null) {
            throw new BusinessException("发票信息不能为空");
        }
        String invoiceNo = normalizeNo(invoice.getInvoiceNo());
        if (countByNo(invoiceNo, null) > 0) {
            throw new BusinessException("发票号已存在: " + invoiceNo);
        }
        invoice.setId(null);
        invoice.setInvoiceNo(invoiceNo);
        if (invoice.getStatus() == null) {
            invoice.setStatus(1);
        }
        invoice.setCreateTime(LocalDateTime.now());
        invoiceMapper.insert(invoice);
        return invoice;
    }

    @Override
    public ReagentInvoice update(ReagentInvoice invoice) {
        if (invoice == null || invoice.getId() == null) {
            throw new BusinessException("发票 ID 不能为空");
        }
        ReagentInvoice existing = invoiceMapper.selectById(invoice.getId());
        if (existing == null) {
            throw new BusinessException("发票不存在: " + invoice.getId());
        }
        if (invoice.getInvoiceNo() != null) {
            String invoiceNo = normalizeNo(invoice.getInvoiceNo());
            if (countByNo(invoiceNo, invoice.getId()) > 0) {
                throw new BusinessException("发票号已存在: " + invoiceNo);
            }
            invoice.setInvoiceNo(invoiceNo);
        }
        invoiceMapper.updateById(invoice);
        return invoiceMapper.selectById(invoice.getId());
    }

    @Override
    public void cancel(Long id) {
        if (id == null) {
            throw new BusinessException("发票 ID 不能为空");
        }
        ReagentInvoice existing = invoiceMapper.selectById(id);
        if (existing == null) {
            throw new BusinessException("发票不存在: " + id);
        }
        ReagentInvoice patch = new ReagentInvoice();
        patch.setId(id);
        patch.setStatus(0);
        invoiceMapper.updateById(patch);
    }

    /** 发票号去空白，空值直接拒绝。 */
    private String normalizeNo(String invoiceNo) {
        if (invoiceNo == null || invoiceNo.trim().isEmpty()) {
            throw new BusinessException("发票号不能为空");
        }
        return invoiceNo.trim();
    }

    /** 按发票号统计条数，excludeId 非空时排除自身（修改场景）。 */
    private long countByNo(String invoiceNo, Long excludeId) {
        LambdaQueryWrapper<ReagentInvoice> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(ReagentInvoice::getInvoiceNo, invoiceNo);
        if (excludeId != null) {
            wrapper.ne(ReagentInvoice::getId, excludeId);
        }
        return invoiceMapper.selectCount(wrapper);
    }
}
