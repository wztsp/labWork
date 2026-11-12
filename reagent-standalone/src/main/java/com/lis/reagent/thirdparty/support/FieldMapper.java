package com.lis.reagent.thirdparty.support;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.lis.reagent.thirdparty.entity.ThirdPartyFieldMap;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 字段映射工具：按配置把本系统字段与对方接口字段互相转换。
 *
 * <p>路径约定：支持 {@code a.b} 形式的嵌套路径；明细字段在本系统侧统一使用
 * {@code items.xxx} 前缀，在对方侧用 {@code items[].xxx} 表示数组元素内的字段。</p>
 */
public final class FieldMapper {

    private static final ObjectMapper MAPPER = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
    private static final String ITEM_PREFIX = "items.";

    private FieldMapper() {
        throw new AssertionError("工具类禁止实例化");
    }

    /**
     * 组装出站请求体。
     *
     * @param header 表头字段（本系统字段名 → 值）
     * @param items  明细字段列表
     * @param maps   出站映射；为空时按本系统标准字段原样输出
     */
    public static ObjectNode buildOutbound(Map<String, Object> header,
                                           List<Map<String, Object>> items,
                                           List<ThirdPartyFieldMap> maps) {
        ObjectNode root = MAPPER.createObjectNode();
        List<ThirdPartyFieldMap> headerMaps = new ArrayList<>();
        List<ThirdPartyFieldMap> itemMaps = new ArrayList<>();
        for (ThirdPartyFieldMap map : maps) {
            if (map.getLocalField() != null && map.getLocalField().startsWith(ITEM_PREFIX)) {
                itemMaps.add(map);
            } else {
                headerMaps.add(map);
            }
        }
        if (headerMaps.isEmpty() && itemMaps.isEmpty()) {
            append(header, root);
            appendItems(root, "items", items, null);
            return root;
        }
        for (ThirdPartyFieldMap map : headerMaps) {
            Object value = valueOf(header.get(map.getLocalField()), map.getDefaultValue());
            putByPath(root, map.getRemoteField(), value);
        }
        if (!items.isEmpty()) {
            String container = resolveItemContainer(itemMaps);
            appendItems(root, container, items, itemMaps);
        }
        return root;
    }

    /**
     * 解析入站响应体：按映射把对方字段写回本系统字段。
     *
     * @param node 已脱壳的业务数据节点
     * @param maps 入站映射
     */
    public static Map<String, Object> parseInbound(JsonNode node, List<ThirdPartyFieldMap> maps) {
        Map<String, Object> result = new LinkedHashMap<>();
        if (node == null || !node.isObject()) {
            return result;
        }
        if (maps.isEmpty()) {
            node.fields().forEachRemaining(e -> result.put(e.getKey(), convert(e.getValue())));
            return result;
        }
        for (ThirdPartyFieldMap map : maps) {
            if (map.getLocalField() != null && map.getLocalField().startsWith(ITEM_PREFIX)) {
                continue;
            }
            JsonNode value = getByPath(node, map.getRemoteField());
            result.put(map.getLocalField(), value == null || value.isNull()
                    ? map.getDefaultValue() : convert(value));
        }
        return result;
    }

    /** 解析单条明细（入站方向），只取 items. 前缀的映射。 */
    public static Map<String, Object> parseInboundItem(JsonNode itemNode, List<ThirdPartyFieldMap> maps) {
        Map<String, Object> result = new LinkedHashMap<>();
        if (itemNode == null || !itemNode.isObject()) {
            return result;
        }
        boolean hasItemMap = false;
        for (ThirdPartyFieldMap map : maps) {
            if (map.getLocalField() == null || !map.getLocalField().startsWith(ITEM_PREFIX)) {
                continue;
            }
            hasItemMap = true;
            String local = map.getLocalField().substring(ITEM_PREFIX.length());
            JsonNode value = getByPath(itemNode, stripItemPrefix(map.getRemoteField()));
            result.put(local, value == null || value.isNull() ? map.getDefaultValue() : convert(value));
        }
        if (!hasItemMap) {
            itemNode.fields().forEachRemaining(e -> result.put(e.getKey(), convert(e.getValue())));
        }
        return result;
    }

    /** 响应脱壳：优先取 data / result 节点，兼容常见包装格式。 */
    public static JsonNode unwrap(JsonNode root) {
        if (root == null || root.isNull()) {
            return root;
        }
        if (root.isObject()) {
            JsonNode data = root.get("data");
            if (data != null && !data.isNull()) {
                return data;
            }
            JsonNode result = root.get("result");
            if (result != null && !result.isNull()) {
                return result;
            }
        }
        return root;
    }

    /** 从脱壳后的节点中取出列表，兼容数组本身与 list/records/rows/items 命名。 */
    public static List<JsonNode> extractList(JsonNode data) {
        List<JsonNode> list = new ArrayList<>();
        if (data == null || data.isNull()) {
            return list;
        }
        JsonNode array = null;
        if (data.isArray()) {
            array = data;
        } else if (data.isObject()) {
            for (String key : new String[]{"list", "records", "rows", "items", "data"}) {
                JsonNode node = data.get(key);
                if (node != null && node.isArray()) {
                    array = node;
                    break;
                }
            }
        }
        if (array != null) {
            array.forEach(list::add);
        }
        return list;
    }

    /** 按路径读取节点，路径不存在返回 null。 */
    public static JsonNode getByPath(JsonNode node, String path) {
        if (node == null || path == null || path.isEmpty()) {
            return null;
        }
        JsonNode current = node;
        for (String segment : path.split("\\.")) {
            if (current == null) {
                return null;
            }
            current = current.get(segment);
        }
        return current;
    }

    /** 按路径写入值，自动创建中间对象。 */
    public static void putByPath(ObjectNode root, String path, Object value) {
        if (path == null || path.trim().isEmpty()) {
            return;
        }
        String[] segments = path.trim().split("\\.");
        ObjectNode current = root;
        for (int i = 0; i < segments.length - 1; i++) {
            JsonNode child = current.get(segments[i]);
            if (child == null || !child.isObject()) {
                child = current.putObject(segments[i]);
            }
            current = (ObjectNode) child;
        }
        String leaf = segments[segments.length - 1];
        current.set(leaf, MAPPER.valueToTree(value));
    }

    private static String resolveItemContainer(List<ThirdPartyFieldMap> itemMaps) {
        for (ThirdPartyFieldMap map : itemMaps) {
            String remote = map.getRemoteField();
            if (remote != null && remote.contains("[]")) {
                String prefix = remote.substring(0, remote.indexOf("[]"));
                if (!prefix.isEmpty()) {
                    return prefix;
                }
            }
        }
        return "items";
    }

    private static void appendItems(ObjectNode root, String container,
                                    List<Map<String, Object>> items, List<ThirdPartyFieldMap> itemMaps) {
        if (items.isEmpty()) {
            return;
        }
        ArrayNode array = MAPPER.createArrayNode();
        for (Map<String, Object> item : items) {
            ObjectNode node = MAPPER.createObjectNode();
            if (itemMaps == null || itemMaps.isEmpty()) {
                append(item, node);
            } else {
                for (ThirdPartyFieldMap map : itemMaps) {
                    String local = map.getLocalField().substring(ITEM_PREFIX.length());
                    Object value = valueOf(item.get(local), map.getDefaultValue());
                    putByPath(node, stripItemPrefix(map.getRemoteField()), value);
                }
            }
            array.add(node);
        }
        putByPath(root, container, array);
    }

    private static String stripItemPrefix(String remoteField) {
        if (remoteField == null) {
            return "";
        }
        String remote = remoteField.trim();
        int idx = remote.indexOf("[].");
        if (idx >= 0) {
            return remote.substring(idx + 3);
        }
        if (remote.endsWith("[]")) {
            return "";
        }
        return remote.startsWith(ITEM_PREFIX) ? remote.substring(ITEM_PREFIX.length()) : remote;
    }

    private static void append(Map<String, Object> values, ObjectNode node) {
        if (values == null) {
            return;
        }
        values.forEach((key, value) -> node.set(key, MAPPER.valueToTree(value)));
    }

    private static Object valueOf(Object value, String defaultValue) {
        if (value == null || (value instanceof String && ((String) value).isEmpty())) {
            return defaultValue;
        }
        return value;
    }

    private static Object convert(JsonNode node) {
        if (node == null || node.isNull()) {
            return null;
        }
        if (node.isTextual()) {
            return node.asText();
        }
        if (node.isBoolean()) {
            return node.asBoolean();
        }
        if (node.isNumber()) {
            BigDecimal decimal = node.decimalValue();
            return decimal.stripTrailingZeros();
        }
        return node.toString();
    }
}
