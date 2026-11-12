package com.lis.instrument.collector.file;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 文件解析器注册表
 * <p>
 * Spring 自动注入所有 {@link FileParser}, 按格式标识索引。
 * 未注册的格式回退到 {@link CsvFileParser}。
 */
@Slf4j
@Component
public class FileParserRegistry {

    private final Map<String, FileParser> parserMap = new HashMap<>();
    private final FileParser fallback;

    @Autowired
    public FileParserRegistry(List<FileParser> parsers) {
        for (FileParser p : parsers) {
            parserMap.put(p.getFormat().toUpperCase(), p);
            log.info("加载文件解析器: {}", p.getFormat());
        }
        fallback = parserMap.get("CSV");
    }

    /**
     * 根据格式标识获取解析器, 未知格式回退到 CSV 通用解析器
     */
    public FileParser getParser(String format) {
        if (format == null || format.isBlank()) {
            return fallback;
        }
        FileParser p = parserMap.get(format.trim().toUpperCase());
        return p != null ? p : fallback;
    }

    /**
     * 列出所有支持的格式
     */
    public java.util.Set<String> listSupportedFormats() {
        return parserMap.keySet();
    }
}