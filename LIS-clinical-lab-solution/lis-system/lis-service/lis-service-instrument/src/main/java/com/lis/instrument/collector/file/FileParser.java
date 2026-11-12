package com.lis.instrument.collector.file;

import java.util.List;
import java.util.Map;

/**
 * 文件结果解析器 (策略模式)
 * <p>
 * 按 {@code fileFormat} 区分实现: CSV / TXT 通用格式, 以及 MINDRAY / ROCHE / ABX 等厂商私有格式。
 */
public interface FileParser {

    /**
     * 支持的格式标识 (不区分大小写)
     */
    String getFormat();

    /**
     * 解析文件内容为结构化结果列表
     * <p>每个 Map 表示一条结果, key 见 {@code ResultConstants}。
     *
     * @param fileName    文件名 (用于日志/识别)
     * @param fileContent 文件全部内容
     * @return 结构化结果列表
     */
    List<Map<String, String>> parse(String fileName, String fileContent);
}