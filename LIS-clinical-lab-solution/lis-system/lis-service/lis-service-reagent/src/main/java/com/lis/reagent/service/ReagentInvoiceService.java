package com.lis.reagent.service;

import com.lis.common.core.result.PageResult;
import com.lis.reagent.entity.ReagentInvoice;

import java.time.LocalDate;

/**
 * 供应商发票服务。
 */
public interface ReagentInvoiceService {

    /**
     * 发票分页查询。
     *
     * @param current    页码，从 1 开始
     * @param size       每页条数
     * @param invoiceNo  发票号，模糊匹配，可为 null
     * @param supplierId 供应商 ID，可为 null
     * @param startDate  开票日期起（含），可为 null
     * @param endDate    开票日期止（含），可为 null
     * @return 分页结果，按开票日期倒序
     */
    PageResult<ReagentInvoice> page(long current, long size, String invoiceNo, Long supplierId,
                                    LocalDate startDate, LocalDate endDate);

    /**
     * 发票登记（发票号唯一）。
     *
     * @param invoice 发票信息，发票号不可为空
     * @return 登记后的发票（含自增 ID 与登记时间）
     */
    ReagentInvoice register(ReagentInvoice invoice);

    /**
     * 发票修改。
     *
     * @param invoice 发票信息，ID 不可为空
     * @return 修改后的发票
     */
    ReagentInvoice update(ReagentInvoice invoice);

    /**
     * 逻辑作废发票（{@code status = 0}）。
     *
     * @param id 发票 ID
     */
    void cancel(Long id);
}
