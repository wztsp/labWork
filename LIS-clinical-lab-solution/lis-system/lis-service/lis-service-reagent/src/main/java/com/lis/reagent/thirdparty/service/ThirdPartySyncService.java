package com.lis.reagent.thirdparty.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.lis.common.core.exception.BusinessException;
import com.lis.reagent.entity.Reagent;
import com.lis.reagent.entity.ReagentPurchaseOrder;
import com.lis.reagent.entity.ReagentPurchaseOrderItem;
import com.lis.reagent.entity.Supplier;
import com.lis.reagent.mapper.ReagentMapper;
import com.lis.reagent.mapper.ReagentPurchaseOrderMapper;
import com.lis.reagent.mapper.SupplierMapper;
import com.lis.reagent.service.ReagentPurchaseOrderService;
import com.lis.reagent.thirdparty.entity.ThirdPartyConfig;
import com.lis.reagent.thirdparty.entity.ThirdPartyFieldMap;
import com.lis.reagent.thirdparty.entity.ThirdPartyProductMap;
import com.lis.reagent.thirdparty.support.FieldMapper;
import com.lis.reagent.thirdparty.support.ThirdPartyHttpClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 三方卫材出站同步：采购订单下发、商品/供应商主数据拉取、库存效期查询。
 *
 * <p>请求组装与响应解析全部走字段映射，对方字段标准不同时只需调整映射配置。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ThirdPartySyncService {

    private final ThirdPartyConfigService configService;
    private final ThirdPartyFieldMapService fieldMapService;
    private final ThirdPartyProductMapService productMapService;
    private final ThirdPartyHttpClient httpClient;
    private final ReagentPurchaseOrderService orderService;
    private final ReagentPurchaseOrderMapper orderMapper;
    private final ReagentMapper reagentMapper;
    private final SupplierMapper supplierMapper;

    /**
     * 采购订单下发：把本系统订单推送给卫材系统，并回写对方单号。
     *
     * @return 对方单号等回执信息
     */
    public Map<String, Object> pushOrder(String partnerCode, Long orderId, String operatorName) {
        ThirdPartyConfig config = configService.requireEnabled(partnerCode);
        ReagentPurchaseOrder order = orderService.getDetail(orderId);
        if (order == null) {
            throw new BusinessException("采购订单不存在: " + orderId);
        }
        if (order.getStatus() != null && order.getStatus() == 4) {
            throw new BusinessException("已取消的订单不能推送");
        }
        Map<String, Object> header = new LinkedHashMap<>();
        header.put("orderNo", order.getOrderNo());
        header.put("supplierName", order.getSupplierName());
        header.put("deptName", order.getDeptName());
        header.put("orderDate", order.getOrderDate());
        header.put("expectDate", order.getExpectDate());
        header.put("totalAmount", order.getTotalAmount());
        header.put("remark", order.getRemark());
        header.put("operatorName", operatorName);

        List<Map<String, Object>> items = new ArrayList<>();
        if (order.getItems() != null) {
            for (ReagentPurchaseOrderItem item : order.getItems()) {
                Map<String, Object> row = new LinkedHashMap<>();
                row.put("reagentCode", item.getReagentCode());
                row.put("reagentName", item.getReagentName());
                row.put("specification", item.getSpecification());
                row.put("quantity", item.getQuantity());
                row.put("unit", item.getUnit());
                row.put("unitPrice", item.getUnitPrice());
                row.put("amount", item.getAmount());
                row.put("remark", item.getRemark());
                items.add(row);
            }
        }
        List<ThirdPartyFieldMap> maps = fieldMapService.list(partnerCode, "ORDER", 1);
        ObjectNode payload = FieldMapper.buildOutbound(header, items, maps);
        JsonNode response = httpClient.call(config, "ORDER", "POST", config.getOrderPath(),
                payload, order.getOrderNo(), operatorName);

        JsonNode data = FieldMapper.unwrap(response);
        String remoteOrderNo = firstText(data, "remoteOrderNo", "orderNo", "orderId", "billNo", "id");
        if (remoteOrderNo != null) {
            order.setThirdPartyCode(partnerCode);
            order.setRemoteOrderNo(remoteOrderNo);
            order.setUpdateTime(LocalDateTime.now());
            orderMapper.updateById(order);
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("orderNo", order.getOrderNo());
        result.put("remoteOrderNo", remoteOrderNo);
        result.put("response", response == null ? null : response.toString());
        return result;
    }

    /** 拉取对方商品主数据，按对方商品编码维护映射。 */
    public int pullProducts(String partnerCode, String operatorName) {
        ThirdPartyConfig config = configService.requireEnabled(partnerCode);
        JsonNode response = httpClient.call(config, "PRODUCT", "GET", config.getProductPath(),
                null, null, operatorName);
        List<JsonNode> nodes = FieldMapper.extractList(FieldMapper.unwrap(response));
        List<ThirdPartyFieldMap> maps = fieldMapService.list(partnerCode, "PRODUCT", 2);
        List<ThirdPartyProductMap> products = new ArrayList<>();
        for (JsonNode node : nodes) {
            Map<String, Object> parsed = FieldMapper.parseInbound(node, maps);
            ThirdPartyProductMap product = new ThirdPartyProductMap();
            product.setRemoteCode(text(parsed, "remoteCode", "code", "productCode", "goodsCode", "skuCode", "itemCode"));
            product.setRemoteName(text(parsed, "remoteName", "name", "productName", "goodsName"));
            product.setRemoteSpec(text(parsed, "remoteSpec", "spec", "specification", "model"));
            product.setRemoteUnit(text(parsed, "remoteUnit", "unit"));
            product.setRemotePrice(decimal(parsed, "remotePrice", "price", "unitPrice", "purchasePrice"));
            if (product.getRemoteCode() != null) {
                products.add(product);
            }
        }
        return productMapService.upsertFromRemote(partnerCode, products);
    }

    /** 拉取对方供应商主数据，按供应商编码新增或更新本系统供应商。 */
    public int pullSuppliers(String partnerCode, String operatorName) {
        ThirdPartyConfig config = configService.requireEnabled(partnerCode);
        JsonNode response = httpClient.call(config, "SUPPLIER", "GET", config.getSupplierPath(),
                null, null, operatorName);
        List<JsonNode> nodes = FieldMapper.extractList(FieldMapper.unwrap(response));
        List<ThirdPartyFieldMap> maps = fieldMapService.list(partnerCode, "SUPPLIER", 2);
        int count = 0;
        for (JsonNode node : nodes) {
            Map<String, Object> parsed = FieldMapper.parseInbound(node, maps);
            String code = text(parsed, "supplierCode", "code", "vendorCode");
            String name = text(parsed, "supplierName", "name", "vendorName");
            if (code == null || name == null) {
                continue;
            }
            Supplier supplier = supplierMapper.selectOne(new LambdaQueryWrapper<Supplier>()
                    .eq(Supplier::getSupplierCode, code).last("limit 1"));
            Supplier target = supplier == null ? new Supplier() : supplier;
            target.setSupplierCode(code);
            target.setSupplierName(name);
            target.setContactPerson(text(parsed, "contactPerson", "contact", "linkman"));
            target.setPhone(text(parsed, "phone", "tel", "telephone"));
            target.setEmail(text(parsed, "email"));
            target.setAddress(text(parsed, "address"));
            target.setLicenseNo(text(parsed, "licenseNo", "businessLicense"));
            target.setMedicalCertNo(text(parsed, "medicalCertNo", "medicalLicense"));
            target.setUpdateTime(LocalDateTime.now());
            if (supplier == null) {
                target.setSourceType(1);
                target.setQualStatus(0);
                target.setStatus(1);
                target.setCreateTime(LocalDateTime.now());
                supplierMapper.insert(target);
            } else {
                supplierMapper.updateById(target);
            }
            count++;
        }
        return count;
    }

    /** 查询对方库存与效期。 */
    public List<Map<String, Object>> queryStock(String partnerCode, String reagentCode, String operatorName) {
        ThirdPartyConfig config = configService.requireEnabled(partnerCode);
        if (reagentCode == null || reagentCode.trim().isEmpty()) {
            throw new BusinessException("试剂编码不能为空");
        }
        String remoteCode = resolveRemoteCode(partnerCode, reagentCode.trim());
        String path = config.getStockPath() + "?reagentCode="
                + URLEncoder.encode(remoteCode, StandardCharsets.UTF_8);
        JsonNode response = httpClient.call(config, "STOCK", "GET", path, null, remoteCode, operatorName);
        List<JsonNode> nodes = FieldMapper.extractList(FieldMapper.unwrap(response));
        List<ThirdPartyFieldMap> maps = fieldMapService.list(partnerCode, "STOCK", 2);
        List<Map<String, Object>> result = new ArrayList<>();
        for (JsonNode node : nodes) {
            Map<String, Object> parsed = FieldMapper.parseInbound(node, maps);
            if (maps.isEmpty() && parsed.isEmpty() && node.isObject()) {
                node.fields().forEachRemaining(e -> parsed.put(e.getKey(), e.getValue().asText()));
            }
            result.add(parsed);
        }
        return result;
    }

    private String resolveRemoteCode(String partnerCode, String reagentCode) {
        Reagent reagent = reagentMapper.selectOne(new LambdaQueryWrapper<Reagent>()
                .eq(Reagent::getReagentCode, reagentCode).last("limit 1"));
        if (reagent != null) {
            ThirdPartyProductMap map = productMapService.findByReagentId(partnerCode, reagent.getId());
            if (map != null) {
                return map.getRemoteCode();
            }
        }
        return reagentCode;
    }

    private String firstText(JsonNode node, String... keys) {
        if (node == null) {
            return null;
        }
        for (String key : keys) {
            JsonNode value = FieldMapper.getByPath(node, key);
            if (value != null && !value.isNull() && !value.asText().isEmpty()) {
                return value.asText();
            }
        }
        return null;
    }

    private String text(Map<String, Object> map, String... keys) {
        for (String key : keys) {
            Object value = map.get(key);
            if (value != null && !value.toString().trim().isEmpty()) {
                return value.toString();
            }
        }
        return null;
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
}
