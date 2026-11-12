package com.lis.instrument.protocol;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 协议解析器工厂
 * <p>
 * Spring 自动注入所有 {@link ProtocolParser} 实现，按 protocolType 索引。
 */
@Slf4j
@Component
public class ProtocolParserFactory {

    private final Map<String, ProtocolParser> parserMap = new HashMap<>();

    @Autowired
    public ProtocolParserFactory(List<ProtocolParser> parsers) {
        for (ProtocolParser p : parsers) {
            parserMap.put(p.getProtocolType().toUpperCase(), p);
            log.info("加载协议解析器: {}", p.getProtocolType());
        }
    }

    /**
     * 根据协议类型获取解析器
     * @throws IllegalArgumentException 不支持的协议类型
     */
    public ProtocolParser getParser(String protocolType) {
        ProtocolParser p = parserMap.get(protocolType.toUpperCase());
        if (p == null) {
            throw new IllegalArgumentException("不支持的协议类型: " + protocolType);
        }
        return p;
    }

    /**
     * 列出所有支持的协议类型
     */
    public java.util.Set<String> listSupportedTypes() {
        return parserMap.keySet();
    }
}
