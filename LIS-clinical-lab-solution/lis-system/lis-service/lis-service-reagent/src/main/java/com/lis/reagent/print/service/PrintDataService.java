package com.lis.reagent.print.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.lis.common.core.exception.BusinessException;
import com.lis.reagent.entity.ReagentInbound;
import com.lis.reagent.entity.ReagentInboundItem;
import com.lis.reagent.entity.ReagentPurchaseApply;
import com.lis.reagent.entity.ReagentPurchaseApplyItem;
import com.lis.reagent.entity.ReagentPurchaseOrder;
import com.lis.reagent.entity.ReagentPurchaseOrderItem;
import com.lis.reagent.entity.ReagentRequisition;
import com.lis.reagent.entity.ReagentRequisitionItem;
import com.lis.reagent.entity.ReagentScrapDisposal;
import com.lis.reagent.entity.ReagentStockCheck;
import com.lis.reagent.entity.ReagentStockCheckItem;
import com.lis.reagent.mapper.ReagentStockCheckItemMapper;
import com.lis.reagent.service.ReagentInboundService;
import com.lis.reagent.service.ReagentPurchaseApplyService;
import com.lis.reagent.service.ReagentPurchaseOrderService;
import com.lis.reagent.service.ReagentRequisitionService;
import com.lis.reagent.service.ReagentScrapDisposalService;
import com.lis.reagent.service.ReagentStockCheckService;
import com.lis.reagent.vo.ApplyDetailVO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 单据打印数据归一化：把各单据实体转换成模板可直接引用的 {@code header} / {@code items}。
 *
 * <p>统一在这里做三件事：字段扁平化、日期与金额转字符串、状态码转中文，
 * 让模板只关心"放哪个字段"，不关心各单据的数据结构差异。</p>
 *
 * @author LIS Team
 */
@Service
@RequiredArgsConstructor
public class PrintDataService {

    private static final DateTimeFormatter DATE_TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private static final Map<Integer, String> APPLY_STATUS = Map.of(
            0, "草稿", 1, "待审批", 2, "审批中", 3, "已批准", 4, "已驳回", 5, "已冻结", 6, "已作废");

    private static final Map<Integer, String> ORDER_STATUS = Map.of(
            0, "待下单", 1, "已下单", 2, "部分到货", 3, "已到货", 4, "已取消");

    private static final Map<Integer, String> APPROVAL_STATUS = Map.of(
            0, "待审批", 1, "已通过", 2, "已驳回");

    private static final Map<Integer, String> VERIFY_STATUS = Map.of(
            0, "待验收", 1, "已验收", 2, "拒收");

    private static final Map<Integer, String> REQ_TYPE = Map.of(
            1, "领用", 2, "退库", 3, "报废", 4, "调拨", 5, "借用", 6, "归还");

    private static final Map<Integer, String> REQ_STATUS = Map.of(
            0, "草稿", 1, "待审", 2, "已审", 3, "已执行", 4, "已驳回", 5, "已作废");

    private static final Map<Integer, String> CHECK_STATUS = Map.of(
            0, "进行中", 1, "已完成", 2, "已作废");

    private final ReagentPurchaseApplyService applyService;
    private final ReagentPurchaseOrderService orderService;
    private final ReagentInboundService inboundService;
    private final ReagentRequisitionService requisitionService;
    private final ReagentStockCheckService stockCheckService;
    private final ReagentScrapDisposalService scrapDisposalService;
    private final ReagentStockCheckItemMapper stockCheckItemMapper;

    /**
     * 构建打印数据。
     *
     * @param bizType 业务类型，见 {@link PrintFieldCatalog}
     * @param id      单据 ID；报损单（SCRAP_DISPOSAL）传报废单（领用单）ID
     */
    public Map<String, Object> buildData(String bizType, Long id) {
        if (id == null) {
            throw new BusinessException("单据 ID 不能为空");
        }
        Map<String, Object> result = new LinkedHashMap<>();
        switch (bizType == null ? "" : bizType) {
            case PrintFieldCatalog.BIZ_PURCHASE_APPLY -> buildApply(id, result);
            case PrintFieldCatalog.BIZ_PURCHASE_ORDER -> buildOrder(id, result);
            case PrintFieldCatalog.BIZ_INBOUND -> buildInbound(id, result);
            case PrintFieldCatalog.BIZ_REQUISITION -> buildRequisition(id, result);
            case PrintFieldCatalog.BIZ_STOCK_CHECK -> buildStockCheck(id, result);
            case PrintFieldCatalog.BIZ_SCRAP_DISPOSAL -> buildScrap(id, result);
            default -> throw new BusinessException("不支持的业务类型：" + bizType);
        }
        result.put("printTime", LocalDateTime.now().format(DATE_TIME));
        return result;
    }

    private void buildApply(Long id, Map<String, Object> result) {
        ApplyDetailVO vo = applyService.getDetail(id);
        if (vo == null || vo.getApply() == null) {
            throw new BusinessException("采购申请单不存在");
        }
        ReagentPurchaseApply apply = vo.getApply();
        Map<String, Object> header = new LinkedHashMap<>();
        header.put("applyNo", s(apply.getApplyNo()));
        header.put("applyDeptName", s(apply.getApplyDeptName()));
        header.put("applyUserName", s(apply.getApplyUserName()));
        header.put("budgetYear", s(apply.getBudgetYear()));
        header.put("totalAmount", s(apply.getTotalAmount()));
        header.put("statusText", text(APPLY_STATUS, apply.getStatus()));
        header.put("urgentFlagText", Integer.valueOf(1).equals(apply.getUrgentFlag()) ? "紧急" : "普通");
        header.put("expectDate", s(apply.getExpectDate()));
        header.put("submitTime", s(apply.getSubmitTime()));
        header.put("finishTime", s(apply.getFinishTime()));
        header.put("remark", s(apply.getRemark()));
        result.put("header", header);

        List<Map<String, Object>> items = new ArrayList<>();
        List<ReagentPurchaseApplyItem> list = vo.getItems();
        int index = 1;
        if (list != null) {
            for (ReagentPurchaseApplyItem item : list) {
                Map<String, Object> row = new LinkedHashMap<>();
                row.put("index", index++);
                row.put("reagentCode", s(item.getReagentCode()));
                row.put("reagentName", s(item.getReagentName()));
                row.put("specification", s(item.getSpecification()));
                row.put("quantity", s(item.getQuantity()));
                row.put("unit", s(item.getUnit()));
                row.put("estimatedPrice", s(item.getEstimatedPrice()));
                row.put("amount", s(item.getAmount()));
                row.put("remark", s(item.getRemark()));
                items.add(row);
            }
        }
        result.put("items", items);
    }

    private void buildOrder(Long id, Map<String, Object> result) {
        ReagentPurchaseOrder order = orderService.getDetail(id);
        if (order == null) {
            throw new BusinessException("采购订单不存在");
        }
        Map<String, Object> header = new LinkedHashMap<>();
        header.put("orderNo", s(order.getOrderNo()));
        header.put("applyNo", s(order.getApplyNo()));
        header.put("supplierName", s(order.getSupplierName()));
        header.put("deptName", s(order.getDeptName()));
        header.put("totalAmount", s(order.getTotalAmount()));
        header.put("orderDate", s(order.getOrderDate()));
        header.put("expectDate", s(order.getExpectDate()));
        header.put("statusText", text(ORDER_STATUS, order.getStatus()));
        header.put("approvalStatusText", text(APPROVAL_STATUS, order.getApprovalStatus()));
        header.put("auditorName", s(order.getAuditorName()));
        header.put("auditTime", s(order.getAuditTime()));
        header.put("operatorName", s(order.getOperatorName()));
        header.put("thirdPartyCode", s(order.getThirdPartyCode()));
        header.put("remoteOrderNo", s(order.getRemoteOrderNo()));
        header.put("remark", s(order.getRemark()));
        result.put("header", header);

        List<Map<String, Object>> items = new ArrayList<>();
        List<ReagentPurchaseOrderItem> list = order.getItems();
        int index = 1;
        if (list != null) {
            for (ReagentPurchaseOrderItem item : list) {
                Map<String, Object> row = new LinkedHashMap<>();
                row.put("index", index++);
                row.put("reagentCode", s(item.getReagentCode()));
                row.put("reagentName", s(item.getReagentName()));
                row.put("specification", s(item.getSpecification()));
                row.put("quantity", s(item.getQuantity()));
                row.put("arrivedQty", s(item.getArrivedQty()));
                row.put("unit", s(item.getUnit()));
                row.put("unitPrice", s(item.getUnitPrice()));
                row.put("amount", s(item.getAmount()));
                row.put("remark", s(item.getRemark()));
                items.add(row);
            }
        }
        result.put("items", items);
    }

    private void buildInbound(Long id, Map<String, Object> result) {
        ReagentInbound inbound = inboundService.getDetail(id);
        if (inbound == null) {
            throw new BusinessException("入库单不存在");
        }
        Map<String, Object> header = new LinkedHashMap<>();
        header.put("inboundNo", s(inbound.getInboundNo()));
        header.put("supplierName", s(inbound.getSupplier()));
        header.put("orderNo", s(inbound.getOrderNo()));
        header.put("totalAmount", s(inbound.getTotalAmount()));
        header.put("verifyStatusText", text(VERIFY_STATUS, inbound.getVerifyStatus()));
        header.put("verifierName", s(inbound.getVerifierName()));
        header.put("verifyTime", s(inbound.getVerifyTime()));
        header.put("inboundMode", s(inbound.getInboundMode()));
        header.put("remark", s(inbound.getRemark()));
        header.put("createTime", s(inbound.getCreateTime()));
        result.put("header", header);

        List<Map<String, Object>> items = new ArrayList<>();
        List<ReagentInboundItem> list = inbound.getItems();
        int index = 1;
        if (list != null) {
            for (ReagentInboundItem item : list) {
                Map<String, Object> row = new LinkedHashMap<>();
                row.put("index", index++);
                row.put("reagentCode", s(item.getReagentCode()));
                row.put("reagentName", s(item.getReagentName()));
                row.put("lotNo", s(item.getLotNo()));
                row.put("barcode", s(item.getBarcode()));
                row.put("quantity", s(item.getQuantity()));
                row.put("unit", s(item.getUnit()));
                row.put("purchasePrice", s(item.getPurchasePrice()));
                row.put("amount", s(item.getAmount()));
                row.put("expiryDate", s(item.getExpiryDate()));
                row.put("location", s(item.getLocation()));
                row.put("remark", s(item.getRemark()));
                items.add(row);
            }
        }
        result.put("items", items);
    }

    private void buildRequisition(Long id, Map<String, Object> result) {
        ReagentRequisition req = requisitionService.getDetail(id);
        if (req == null) {
            throw new BusinessException("领用/出库单不存在");
        }
        Map<String, Object> header = reqHeader(req);
        result.put("header", header);
        result.put("items", reqItems(req.getItems()));
    }

    /** 报损单：id 为报废单（领用单）ID，处置信息可能尚未登记 */
    private void buildScrap(Long id, Map<String, Object> result) {
        ReagentRequisition req = requisitionService.getDetail(id);
        if (req == null) {
            throw new BusinessException("报废单不存在");
        }
        Map<String, Object> header = reqHeader(req);
        ReagentScrapDisposal disposal = scrapDisposalService.getByReqId(id);
        header.put("disposalMethod", disposal == null ? "" : s(disposal.getDisposalMethod()));
        header.put("disposalOrg", disposal == null ? "" : s(disposal.getDisposalOrg()));
        header.put("disposalCert", disposal == null ? "" : s(disposal.getDisposalCert()));
        header.put("disposalDate", disposal == null ? "" : s(disposal.getDisposalDate()));
        boolean hazard = disposal != null
                ? Integer.valueOf(1).equals(disposal.getHazardFlag())
                : Integer.valueOf(1).equals(req.getHazardFlag());
        header.put("hazardText", hazard ? "是" : "否");
        header.put("safetyApproverName", disposal == null ? "" : s(disposal.getSafetyApproverName()));
        header.put("safetyApproveTime", disposal == null ? "" : s(disposal.getSafetyApproveTime()));
        result.put("header", header);
        result.put("items", reqItems(req.getItems()));
    }

    private Map<String, Object> reqHeader(ReagentRequisition req) {
        Map<String, Object> header = new LinkedHashMap<>();
        header.put("reqNo", s(req.getReqNo()));
        header.put("reqTypeText", text(REQ_TYPE, req.getReqType()));
        header.put("deptName", s(req.getDeptName()));
        header.put("applyUserName", s(req.getApplyUserName()));
        header.put("statusText", text(REQ_STATUS, req.getStatus()));
        header.put("approverName", s(req.getApproverName()));
        header.put("approveTime", s(req.getApproveTime()));
        header.put("executorName", s(req.getExecutorName()));
        header.put("executeTime", s(req.getExecuteTime()));
        header.put("supplierName", s(req.getSupplierName()));
        header.put("reason", s(req.getReason()));
        header.put("totalAmount", s(req.getTotalAmount()));
        header.put("remark", s(req.getRemark()));
        header.put("createTime", s(req.getCreateTime()));
        return header;
    }

    private List<Map<String, Object>> reqItems(List<ReagentRequisitionItem> list) {
        List<Map<String, Object>> items = new ArrayList<>();
        int index = 1;
        if (list != null) {
            for (ReagentRequisitionItem item : list) {
                Map<String, Object> row = new LinkedHashMap<>();
                row.put("index", index++);
                row.put("reagentCode", s(item.getReagentCode()));
                row.put("reagentName", s(item.getReagentName()));
                row.put("lotNo", s(item.getLotNo()));
                row.put("expiryDate", s(item.getExpiryDate()));
                row.put("quantity", s(item.getQuantity()));
                row.put("unit", s(item.getUnit()));
                row.put("unitPrice", s(item.getUnitPrice()));
                row.put("amount", s(item.getAmount()));
                row.put("remark", s(item.getRemark()));
                items.add(row);
            }
        }
        return items;
    }

    private void buildStockCheck(Long id, Map<String, Object> result) {
        ReagentStockCheck check = stockCheckService.getById(id);
        if (check == null) {
            throw new BusinessException("盘点单不存在");
        }
        Map<String, Object> header = new LinkedHashMap<>();
        header.put("checkNo", s(check.getCheckNo()));
        header.put("warehouse", s(check.getWarehouse()));
        header.put("checkDate", s(check.getCheckDate()));
        header.put("statusText", text(CHECK_STATUS, check.getStatus()));
        header.put("auditStatusText", Integer.valueOf(1).equals(check.getAuditStatus()) ? "已审核" : "未审核");
        header.put("operatorName", s(check.getOperatorName()));
        header.put("auditorName", s(check.getAuditorName()));
        header.put("auditTime", s(check.getAuditTime()));
        header.put("remark", s(check.getRemark()));
        result.put("header", header);

        LambdaQueryWrapper<ReagentStockCheckItem> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(ReagentStockCheckItem::getCheckId, id).orderByAsc(ReagentStockCheckItem::getId);
        List<ReagentStockCheckItem> list = stockCheckItemMapper.selectList(wrapper);
        List<Map<String, Object>> items = new ArrayList<>();
        int index = 1;
        for (ReagentStockCheckItem item : list) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("index", index++);
            row.put("reagentCode", s(item.getReagentCode()));
            row.put("reagentName", s(item.getReagentName()));
            row.put("lotNo", s(item.getLotNo()));
            row.put("bookQty", s(item.getBookQty()));
            row.put("actualQty", s(item.getActualQty()));
            row.put("diffQty", s(item.getDiffQty()));
            row.put("diffReason", s(item.getDiffReason()));
            row.put("remark", s(item.getRemark()));
            items.add(row);
        }
        result.put("items", items);
    }

    /** 状态码转中文，未知值原样输出 */
    private static String text(Map<Integer, String> dict, Integer code) {
        if (code == null) {
            return "";
        }
        return dict.getOrDefault(code, String.valueOf(code));
    }

    /** 统一转字符串：金额去尾零、日期按固定格式、null 输出空串 */
    private static String s(Object value) {
        if (value == null) {
            return "";
        }
        if (value instanceof BigDecimal decimal) {
            return decimal.stripTrailingZeros().toPlainString();
        }
        if (value instanceof LocalDateTime dateTime) {
            return dateTime.format(DATE_TIME);
        }
        if (value instanceof LocalDate date) {
            return date.toString();
        }
        return String.valueOf(value);
    }
}
