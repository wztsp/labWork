package com.lis.reagent.thirdparty.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.lis.common.core.exception.BusinessException;
import com.lis.reagent.entity.Reagent;
import com.lis.reagent.entity.ReagentInbound;
import com.lis.reagent.entity.ReagentInboundItem;
import com.lis.reagent.entity.ReagentPurchaseOrder;
import com.lis.reagent.entity.ReagentPurchaseOrderItem;
import com.lis.reagent.mapper.ReagentMapper;
import com.lis.reagent.mapper.ReagentPurchaseOrderItemMapper;
import com.lis.reagent.mapper.ReagentPurchaseOrderMapper;
import com.lis.reagent.service.ReagentInboundService;
import com.lis.reagent.thirdparty.entity.ThirdPartyConfig;
import com.lis.reagent.thirdparty.entity.ThirdPartyFieldMap;
import com.lis.reagent.thirdparty.support.FieldMapper;
import com.lis.reagent.thirdparty.support.ThirdPartyHttpClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 三方卫材入站接口：对方回传到货、订单状态，或拉取本系统订单与试剂主数据。
 *
 * <p>到货回传直接生成「待验收」入库单，由检验人员在入库验收页面确认后入库，
 * 不跳过验收环节。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ThirdPartyInboundService {

    private final ThirdPartyConfigService configService;
    private final ThirdPartyFieldMapService fieldMapService;
    private final ThirdPartyProductMapService productMapService;
    private final ThirdPartyHttpClient httpClient;
    private final ReagentInboundService inboundService;
    private final ReagentPurchaseOrderMapper orderMapper;
    private final ReagentPurchaseOrderItemMapper orderItemMapper;
    private final ReagentMapper reagentMapper;

    /** 校验入站开关与 Token，返回对接配置。 */
    public ThirdPartyConfig authorize(String partnerCode, String token) {
        ThirdPartyConfig config = configService.getByCode(partnerCode);
        if (config.getEnabled() == null || config.getEnabled() != 1) {
            throw new BusinessException("对接方已停用: " + partnerCode);
        }
        if (config.getInboundEnabled() == null || config.getInboundEnabled() != 1) {
            throw new BusinessException("对接方未开放入站接口: " + partnerCode);
        }
        String expected = config.getInboundToken();
        if (expected == null || expected.trim().isEmpty() || !secureEquals(expected, token)) {
            throw new BusinessException("入站 Token 校验失败");
        }
        return config;
    }

    /**
     * 到货回传：生成待验收入库单。
     *
     * @param body        对方推送的到货数据
     * @param requestBody 原始报文（用于日志留痕）
     * @param url         请求地址（用于日志留痕）
     */
    public Map<String, Object> receiveDelivery(ThirdPartyConfig config, JsonNode body,
                                               String requestBody, String url) {
        try {
            List<ThirdPartyFieldMap> maps = fieldMapService.list(config.getPartnerCode(), "DELIVERY", 2);
            JsonNode data = FieldMapper.unwrap(body);
            Map<String, Object> header = FieldMapper.parseInbound(data, maps);
            String deliveryNo = text(header, "deliveryNo", "deliveryCode", "billNo");
            String orderNo = text(header, "orderNo", "purchaseOrderNo");

            List<ReagentInboundItem> items = new ArrayList<>();
            BigDecimal totalAmount = BigDecimal.ZERO;
            List<String> unknown = new ArrayList<>();
            for (JsonNode itemNode : extractItemNodes(data)) {
                Map<String, Object> parsed = FieldMapper.parseInboundItem(itemNode, maps);
                String remoteCode = text(parsed, "remoteCode", "reagentCode", "code",
                        "productCode", "goodsCode", "skuCode");
                Long reagentId = productMapService.resolveReagentId(config.getPartnerCode(), remoteCode);
                if (reagentId == null) {
                    unknown.add(remoteCode == null ? "(空编码)" : remoteCode);
                    continue;
                }
                BigDecimal quantity = decimal(parsed, "quantity", "qty", "amountQty");
                if (quantity == null || quantity.compareTo(BigDecimal.ZERO) <= 0) {
                    throw new BusinessException("到货数量异常: " + remoteCode);
                }
                ReagentInboundItem item = new ReagentInboundItem();
                item.setReagentId(reagentId);
                item.setLotNo(text(parsed, "lotNo", "batchNo", "lot"));
                item.setQuantity(quantity);
                item.setUnit(text(parsed, "unit"));
                item.setPurchasePrice(decimal(parsed, "purchasePrice", "unitPrice", "price"));
                item.setExpiryDate(date(parsed, "expiryDate", "expireDate", "validUntil"));
                item.setLocation(text(parsed, "location", "storageLocation"));
                item.setRemark(text(parsed, "remark"));
                if (item.getPurchasePrice() != null) {
                    totalAmount = totalAmount.add(item.getPurchasePrice().multiply(quantity));
                }
                items.add(item);
            }
            if (!unknown.isEmpty()) {
                throw new BusinessException("以下对方商品编码未建立映射，无法入库: " + String.join(", ", unknown));
            }
            if (items.isEmpty()) {
                throw new BusinessException("到货明细为空");
            }

            ReagentInbound inbound = new ReagentInbound();
            inbound.setSupplier(text(header, "supplierName", "supplier", "vendorName"));
            inbound.setTotalAmount(totalAmount);
            inbound.setInboundMode("THIRD_PARTY");
            inbound.setRemark("三方卫材到货回传: " + (deliveryNo == null ? "" : deliveryNo)
                    + (orderNo == null ? "" : " / 订单 " + orderNo));
            if (orderNo != null) {
                ReagentPurchaseOrder order = orderMapper.selectOne(new LambdaQueryWrapper<ReagentPurchaseOrder>()
                        .eq(ReagentPurchaseOrder::getOrderNo, orderNo).last("limit 1"));
                if (order != null) {
                    inbound.setOrderId(order.getId());
                    inbound.setOrderNo(order.getOrderNo());
                    inbound.setSupplierId(order.getSupplierId());
                    fillOrderItemId(items, order);
                }
            }
            ReagentInbound created = inboundService.createScanInbound("THIRD_PARTY", items, inbound.getRemark());
            created.setSupplier(inbound.getSupplier());
            created.setTotalAmount(totalAmount);
            created.setOrderId(inbound.getOrderId());
            created.setOrderNo(inbound.getOrderNo());
            created.setSupplierId(inbound.getSupplierId());
            inboundService.updateById(created);

            Map<String, Object> result = new LinkedHashMap<>();
            result.put("inboundId", created.getId());
            result.put("inboundNo", created.getInboundNo());
            result.put("itemCount", items.size());
            httpClient.logInbound(config.getPartnerCode(), "DELIVERY", "POST", url,
                    requestBody, result.toString(), true, deliveryNo, null);
            return result;
        } catch (Exception e) {
            httpClient.logInbound(config.getPartnerCode(), "DELIVERY", "POST", url,
                    requestBody, null, false, null, e.getMessage());
            throw e;
        }
    }

    /**
     * 订单状态回传：按订单号回写对方单号与状态。
     */
    public Map<String, Object> updateOrderStatus(ThirdPartyConfig config, JsonNode body,
                                                 String requestBody, String url) {
        try {
            List<ThirdPartyFieldMap> maps = fieldMapService.list(config.getPartnerCode(), "ORDER", 2);
            Map<String, Object> parsed = FieldMapper.parseInbound(FieldMapper.unwrap(body), maps);
            String orderNo = text(parsed, "orderNo", "purchaseOrderNo");
            if (orderNo == null) {
                throw new BusinessException("订单号不能为空");
            }
            ReagentPurchaseOrder order = orderMapper.selectOne(new LambdaQueryWrapper<ReagentPurchaseOrder>()
                    .eq(ReagentPurchaseOrder::getOrderNo, orderNo).last("limit 1"));
            if (order == null) {
                throw new BusinessException("采购订单不存在: " + orderNo);
            }
            String remoteOrderNo = text(parsed, "remoteOrderNo", "thirdOrderNo", "supplierOrderNo");
            if (remoteOrderNo != null) {
                order.setRemoteOrderNo(remoteOrderNo);
            }
            Integer status = status(parsed, "status", "orderStatus");
            if (status != null) {
                order.setStatus(status);
            }
            order.setThirdPartyCode(config.getPartnerCode());
            order.setUpdateTime(LocalDateTime.now());
            orderMapper.updateById(order);

            Map<String, Object> result = new LinkedHashMap<>();
            result.put("orderNo", orderNo);
            result.put("status", order.getStatus());
            httpClient.logInbound(config.getPartnerCode(), "ORDER", "POST", url,
                    requestBody, result.toString(), true, orderNo, null);
            return result;
        } catch (Exception e) {
            httpClient.logInbound(config.getPartnerCode(), "ORDER", "POST", url,
                    requestBody, null, false, null, e.getMessage());
            throw e;
        }
    }

    /** 供对方拉取本系统采购订单（默认取未取消的订单）。 */
    public List<Map<String, Object>> listOrders(ThirdPartyConfig config, Integer status, String url) {
        LambdaQueryWrapper<ReagentPurchaseOrder> wrapper = new LambdaQueryWrapper<>();
        wrapper.ne(ReagentPurchaseOrder::getStatus, 4);
        if (status != null) {
            wrapper.eq(ReagentPurchaseOrder::getStatus, status);
        }
        wrapper.orderByDesc(ReagentPurchaseOrder::getId).last("limit 500");
        List<ReagentPurchaseOrder> orders = orderMapper.selectList(wrapper);
        List<Map<String, Object>> result = new ArrayList<>();
        for (ReagentPurchaseOrder order : orders) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("orderNo", order.getOrderNo());
            row.put("supplierName", order.getSupplierName());
            row.put("deptName", order.getDeptName());
            row.put("orderDate", order.getOrderDate());
            row.put("expectDate", order.getExpectDate());
            row.put("totalAmount", order.getTotalAmount());
            row.put("status", order.getStatus());
            row.put("remark", order.getRemark());
            row.put("thirdPartyCode", order.getThirdPartyCode());
            row.put("remoteOrderNo", order.getRemoteOrderNo());
            List<Map<String, Object>> items = new ArrayList<>();
            List<ReagentPurchaseOrderItem> itemList = orderItemMapper.selectList(
                    new LambdaQueryWrapper<ReagentPurchaseOrderItem>()
                            .eq(ReagentPurchaseOrderItem::getOrderId, order.getId()));
            for (ReagentPurchaseOrderItem item : itemList) {
                Map<String, Object> itemRow = new LinkedHashMap<>();
                itemRow.put("reagentCode", item.getReagentCode());
                itemRow.put("reagentName", item.getReagentName());
                itemRow.put("specification", item.getSpecification());
                itemRow.put("quantity", item.getQuantity());
                itemRow.put("unit", item.getUnit());
                itemRow.put("unitPrice", item.getUnitPrice());
                itemRow.put("amount", item.getAmount());
                items.add(itemRow);
            }
            row.put("items", items);
            result.add(row);
        }
        httpClient.logInbound(config.getPartnerCode(), "ORDER", "GET", url, null,
                "共 " + result.size() + " 单", true, null, null);
        return result;
    }

    /** 供对方拉取本系统试剂主数据。 */
    public List<Map<String, Object>> listReagents(ThirdPartyConfig config, String url) {
        List<Reagent> reagents = reagentMapper.selectList(new LambdaQueryWrapper<Reagent>()
                .eq(Reagent::getStatus, 1).orderByAsc(Reagent::getReagentCode).last("limit 2000"));
        List<Map<String, Object>> result = new ArrayList<>();
        for (Reagent reagent : reagents) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("reagentCode", reagent.getReagentCode());
            row.put("reagentName", reagent.getReagentName());
            row.put("specification", reagent.getSpecification());
            row.put("unit", reagent.getUnit());
            row.put("manufacturer", reagent.getManufacturer());
            row.put("pricePurchase", reagent.getPricePurchase());
            result.add(row);
        }
        httpClient.logInbound(config.getPartnerCode(), "PRODUCT", "GET", url, null,
                "共 " + result.size() + " 条", true, null, null);
        return result;
    }

    private void fillOrderItemId(List<ReagentInboundItem> items, ReagentPurchaseOrder order) {
        List<ReagentPurchaseOrderItem> orderItems = order.getItems();
        if (orderItems == null || orderItems.isEmpty()) {
            return;
        }
        for (ReagentInboundItem item : items) {
            for (ReagentPurchaseOrderItem orderItem : orderItems) {
                if (item.getReagentId().equals(orderItem.getReagentId()) && item.getOrderItemId() == null) {
                    item.setOrderItemId(orderItem.getId());
                    break;
                }
            }
        }
    }

    private List<JsonNode> extractItemNodes(JsonNode data) {
        List<JsonNode> items = FieldMapper.extractList(data);
        if (!items.isEmpty()) {
            return items;
        }
        for (String key : new String[]{"items", "details", "deliveryItems"}) {
            JsonNode node = data == null ? null : data.get(key);
            if (node != null && node.isArray()) {
                List<JsonNode> list = new ArrayList<>();
                node.forEach(list::add);
                return list;
            }
        }
        return items;
    }

    private Integer status(Map<String, Object> map, String... keys) {
        String text = text(map, keys);
        if (text == null) {
            return null;
        }
        try {
            int value = Integer.parseInt(text);
            return value >= 0 && value <= 4 ? value : null;
        } catch (NumberFormatException e) {
            if (text.contains("取消")) return 4;
            if (text.contains("到货") || text.contains("完成")) return 3;
            if (text.contains("发货") || text.contains("部分")) return 2;
            if (text.contains("下单")) return 1;
            return null;
        }
    }

    private LocalDate date(Map<String, Object> map, String... keys) {
        String text = text(map, keys);
        if (text == null) {
            return null;
        }
        try {
            return LocalDate.parse(text.length() > 10 ? text.substring(0, 10) : text);
        } catch (Exception e) {
            return null;
        }
    }

    private BigDecimal decimal(Map<String, Object> map, String... keys) {
        String text = text(map, keys);
        if (text == null) {
            return null;
        }
        try {
            return new BigDecimal(text);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private String text(Map<String, Object> map, String... keys) {
        for (String key : keys) {
            Object value = map.get(key);
            if (value != null && !value.toString().trim().isEmpty()) {
                return value.toString().trim();
            }
        }
        return null;
    }

    private boolean secureEquals(String expected, String actual) {
        if (actual == null) {
            return false;
        }
        return MessageDigest.isEqual(expected.getBytes(StandardCharsets.UTF_8),
                actual.getBytes(StandardCharsets.UTF_8));
    }
}
