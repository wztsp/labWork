package com.lis.instrument.protocol;

import java.util.List;
import java.util.Map;

/**
 * 协议解析器接口 (策略模式)
 */
public interface ProtocolParser {

    /**
     * 协议类型: ASTM / HL7 / POCT / FHIR 等
     */
    String getProtocolType();

    /**
     * 解析原始消息为结构化字段列表
     * 每个 Map 表示一条结果记录 (key=字段名, value=字段值)
     */
    List<Map<String, String>> parse(String rawMessage);
}
