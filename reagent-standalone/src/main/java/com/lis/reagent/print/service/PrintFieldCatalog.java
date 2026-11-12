package com.lis.reagent.print.service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 打印字段字典：定义每类业务可用的打印字段。
 *
 * <p>设计器左侧字段面板、模板保存时的字段校验都以此为准；
 * 字段名与 {@link PrintDataService} 输出的 key 必须一致。</p>
 *
 * @author LIS Team
 */
public final class PrintFieldCatalog {

    /** 业务类型：试剂条码标签 */
    public static final String BIZ_LABEL = "REAGENT_LABEL";
    public static final String BIZ_PURCHASE_APPLY = "PURCHASE_APPLY";
    public static final String BIZ_PURCHASE_ORDER = "PURCHASE_ORDER";
    public static final String BIZ_INBOUND = "INBOUND";
    public static final String BIZ_REQUISITION = "REQUISITION";
    public static final String BIZ_STOCK_CHECK = "STOCK_CHECK";
    public static final String BIZ_SCRAP_DISPOSAL = "SCRAP_DISPOSAL";

    private static final Map<String, String> BIZ_NAMES = new LinkedHashMap<>();
    private static final Map<String, Map<String, Object>> CATALOG = new LinkedHashMap<>();

    static {
        BIZ_NAMES.put(BIZ_LABEL, "试剂条码标签");
        BIZ_NAMES.put(BIZ_PURCHASE_APPLY, "采购申请单");
        BIZ_NAMES.put(BIZ_PURCHASE_ORDER, "采购订单");
        BIZ_NAMES.put(BIZ_INBOUND, "试剂入库单");
        BIZ_NAMES.put(BIZ_REQUISITION, "领用/出库单");
        BIZ_NAMES.put(BIZ_STOCK_CHECK, "库存盘点单");
        BIZ_NAMES.put(BIZ_SCRAP_DISPOSAL, "报损单");

        CATALOG.put(BIZ_LABEL, group(
                fields(f("barcode", "条码"), f("reagentCode", "试剂编码"), f("reagentName", "试剂名称"),
                        f("specification", "规格"), f("lotNo", "批号"), f("quantity", "数量"), f("unit", "单位"),
                        f("expiryDate", "有效期至"), f("location", "库位"), f("inboundNo", "入库单号"),
                        f("orderNo", "采购订单号"), f("supplierName", "供应商")),
                list(), meta()));

        CATALOG.put(BIZ_PURCHASE_APPLY, group(
                fields(f("applyNo", "申请单号"), f("applyDeptName", "申请科室"), f("applyUserName", "申请人"),
                        f("budgetYear", "预算年度"), f("totalAmount", "申请金额"), f("statusText", "状态"),
                        f("urgentFlagText", "紧急程度"), f("expectDate", "期望到货日期"), f("submitTime", "提交时间"),
                        f("finishTime", "完成时间"), f("remark", "备注")),
                fields(f("reagentCode", "试剂编码"), f("reagentName", "试剂名称"), f("specification", "规格"),
                        f("quantity", "数量"), f("unit", "单位"), f("estimatedPrice", "预估单价"),
                        f("amount", "金额"), f("remark", "备注")),
                meta()));

        CATALOG.put(BIZ_PURCHASE_ORDER, group(
                fields(f("orderNo", "订单编号"), f("applyNo", "来源申请单"), f("supplierName", "供应商"),
                        f("deptName", "请购科室"), f("totalAmount", "订单金额"), f("orderDate", "下单日期"),
                        f("expectDate", "期望到货日期"), f("statusText", "订单状态"),
                        f("approvalStatusText", "审批状态"), f("auditorName", "审核人"), f("auditTime", "审核时间"),
                        f("operatorName", "经办人"), f("thirdPartyCode", "对接方编码"),
                        f("remoteOrderNo", "对方单号"), f("remark", "备注")),
                fields(f("reagentCode", "试剂编码"), f("reagentName", "试剂名称"), f("specification", "规格"),
                        f("quantity", "数量"), f("arrivedQty", "已到货数量"), f("unit", "单位"),
                        f("unitPrice", "单价"), f("amount", "金额"), f("remark", "备注")),
                meta()));

        CATALOG.put(BIZ_INBOUND, group(
                fields(f("inboundNo", "入库单号"), f("supplierName", "供应商"), f("orderNo", "采购订单号"),
                        f("totalAmount", "入库金额"), f("verifyStatusText", "验收状态"),
                        f("verifierName", "验收人"), f("verifyTime", "验收时间"), f("inboundMode", "入库方式"),
                        f("remark", "备注"), f("createTime", "创建时间")),
                fields(f("reagentCode", "试剂编码"), f("reagentName", "试剂名称"), f("lotNo", "批号"),
                        f("barcode", "条码"), f("quantity", "数量"), f("unit", "单位"),
                        f("purchasePrice", "单价"), f("amount", "金额"), f("expiryDate", "有效期至"),
                        f("location", "库位"), f("remark", "备注")),
                meta()));

        CATALOG.put(BIZ_REQUISITION, group(
                fields(f("reqNo", "单据编号"), f("reqTypeText", "单据类型"), f("deptName", "领用科室"),
                        f("applyUserName", "领用人"), f("statusText", "单据状态"), f("approverName", "审核人"),
                        f("approveTime", "审核时间"), f("executorName", "执行人"), f("executeTime", "执行时间"),
                        f("supplierName", "供应商"), f("reason", "领用原因"), f("totalAmount", "单据金额"),
                        f("remark", "备注"), f("createTime", "创建时间")),
                fields(f("reagentCode", "试剂编码"), f("reagentName", "试剂名称"), f("lotNo", "批号"),
                        f("expiryDate", "有效期至"), f("quantity", "数量"), f("unit", "单位"),
                        f("unitPrice", "单价"), f("amount", "金额"), f("remark", "备注")),
                meta()));

        CATALOG.put(BIZ_STOCK_CHECK, group(
                fields(f("checkNo", "盘点单号"), f("warehouse", "盘点仓库"), f("checkDate", "盘点日期"),
                        f("statusText", "盘点状态"), f("auditStatusText", "审核状态"),
                        f("operatorName", "盘点人"), f("auditorName", "审核人"), f("auditTime", "审核时间"),
                        f("remark", "备注")),
                fields(f("reagentCode", "试剂编码"), f("reagentName", "试剂名称"), f("lotNo", "批号"),
                        f("bookQty", "账面数量"), f("actualQty", "实盘数量"), f("diffQty", "差异数量"),
                        f("diffReason", "差异原因"), f("remark", "备注")),
                meta()));

        CATALOG.put(BIZ_SCRAP_DISPOSAL, group(
                fields(f("reqNo", "报废单号"), f("deptName", "报损科室"), f("applyUserName", "经办人"),
                        f("createTime", "登记时间"), f("disposalMethod", "处置方式"), f("disposalOrg", "处置单位"),
                        f("disposalCert", "处置凭证"), f("disposalDate", "处置日期"), f("hazardText", "是否危险品"),
                        f("safetyApproverName", "安全员"), f("safetyApproveTime", "安全审批时间"),
                        f("reason", "报损原因"), f("totalAmount", "报损金额"), f("remark", "备注")),
                fields(f("reagentCode", "试剂编码"), f("reagentName", "试剂名称"), f("lotNo", "批号"),
                        f("expiryDate", "有效期至"), f("quantity", "数量"), f("unit", "单位"),
                        f("amount", "金额"), f("remark", "备注")),
                meta()));
    }

    private PrintFieldCatalog() {
    }

    /** 是否支持该业务类型 */
    public static boolean supports(String bizType) {
        return bizType != null && CATALOG.containsKey(bizType);
    }

    /** 业务类型列表（编码 + 名称），供前端筛选下拉使用 */
    public static List<Map<String, Object>> bizTypes() {
        List<Map<String, Object>> list = new ArrayList<>();
        BIZ_NAMES.forEach((code, name) -> {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("code", code);
            item.put("name", name);
            list.add(item);
        });
        return list;
    }

    /** 某业务类型的字段字典：header / items / meta */
    public static Map<String, Object> describe(String bizType) {
        Map<String, Object> data = CATALOG.get(bizType);
        if (data == null) {
            return null;
        }
        Map<String, Object> result = new LinkedHashMap<>(data);
        result.put("bizType", bizType);
        result.put("bizName", BIZ_NAMES.get(bizType));
        return result;
    }

    private static Map<String, Object> group(List<Map<String, Object>> header,
                                             List<Map<String, Object>> items,
                                             List<Map<String, Object>> meta) {
        Map<String, Object> g = new LinkedHashMap<>();
        g.put("header", header);
        g.put("items", items);
        g.put("meta", meta);
        return g;
    }

    private static List<Map<String, Object>> meta() {
        return fields(f("printTime", "打印时间"));
    }

    private static Map<String, Object> f(String field, String title) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("field", field);
        m.put("title", title);
        return m;
    }

    private static List<Map<String, Object>> fields(Map<String, Object>... items) {
        List<Map<String, Object>> list = new ArrayList<>();
        for (Map<String, Object> item : items) {
            list.add(item);
        }
        return list;
    }

    private static List<Map<String, Object>> list() {
        return new ArrayList<>();
    }
}
