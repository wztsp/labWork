package com.lis.instrument.collector;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lis.instrument.collector.file.FileParser;
import com.lis.instrument.collector.file.FileParserRegistry;
import com.lis.instrument.entity.Instrument;
import com.lis.instrument.entity.InstrumentCommLog;
import com.lis.instrument.matching.ResultMatchingService;
import com.lis.instrument.service.CommLogService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * 本地文件采集器 (connectType=FILE)
 * <p>
 * 轮询 {@code filePath} 目录下匹配 {@code filePattern} 的文件, 读取内容经
 * {@link FileParserRegistry} 解析为结构化结果, 交给 {@code ResultMatchingService.matchAndSave}。
 * 处理成功后按水位线策略将文件搬移到 {@code processedDir} 归档 (未配置则删除), 避免重复读取。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class FileResultCollector implements ResultCollector {

    private final FileParserRegistry parserRegistry;
    private final ResultMatchingService resultMatchingService;
    private final CommLogService commLogService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public String getConnectType() {
        return "FILE";
    }

    @Override
    public void collect(Instrument instrument) {
        String dir = instrument.getFilePath();
        if (dir == null || dir.isBlank()) {
            log.warn("仪器 [{}] 未配置 file_path, 跳过文件采集", instrument.getId());
            return;
        }
        String format = instrument.getFileFormat() != null ? instrument.getFileFormat() : "CSV";
        FileParser parser = parserRegistry.getParser(format);
        Pattern pattern = compilePattern(instrument.getFilePattern());

        List<Path> files = listCandidateFiles(dir, pattern);
        if (files.isEmpty()) {
            return;
        }
        log.info("仪器 [{}] 发现待处理文件 {} 个 (dir={}, format={})",
                instrument.getId(), files.size(), dir, format);
        for (Path file : files) {
            processFile(instrument, parser, file);
        }
    }

    private void processFile(Instrument instrument, FileParser parser, Path file) {
        String parseStatus = "SUCCESS";
        String parseResult = null;
        try {
            String content = Files.readString(file, StandardCharsets.UTF_8);
            List<Map<String, String>> results = parser.parse(file.getFileName().toString(), content);
            parseResult = objectMapper.writeValueAsString(results);
            if (results.isEmpty()) {
                log.info("仪器 [{}] 文件 [{}] 无有效结果, 仍归档", instrument.getId(), file.getFileName());
            } else {
                resultMatchingService.matchAndSave(instrument.getId(), instrument.getProtocolType(), results);
            }
            // 去重: 搬移到归档目录 (未配置则删除)
            relocate(file, instrument.getProcessedDir());
        } catch (Exception e) {
            parseStatus = "FAIL";
            parseResult = "{\"error\":\"" + e.getMessage() + "\"}";
            log.error("仪器 [{}] 文件 [{}] 处理失败", instrument.getId(), file.getFileName(), e);
        } finally {
            saveCommLog(instrument, file, parseStatus, parseResult);
        }
    }

    /** 搬移已处理文件到归档目录; 未配置归档目录则直接删除 */
    private void relocate(Path file, String processedDir) throws IOException {
        if (processedDir != null && !processedDir.isBlank()) {
            Path targetDir = Paths.get(processedDir);
            Files.createDirectories(targetDir);
            Files.move(file, targetDir.resolve(file.getFileName()),
                    StandardCopyOption.REPLACE_EXISTING);
            log.info("文件 [{}] 已归档到 {}", file.getFileName(), targetDir);
        } else {
            Files.deleteIfExists(file);
            log.info("文件 [{}] 已删除 (未配置归档目录)", file.getFileName());
        }
    }

    private void saveCommLog(Instrument instrument, Path file, String parseStatus, String parseResult) {
        try {
            InstrumentCommLog commLog = new InstrumentCommLog();
            commLog.setInstrumentId(instrument.getId());
            commLog.setDirection("RECEIVE");
            commLog.setProtocolType(instrument.getProtocolType());
            commLog.setRawMessage("FILE: " + file.getFileName());
            commLog.setParseStatus(parseStatus);
            commLog.setParseResult(parseResult);
            commLog.setMessageTime(LocalDateTime.now());
            commLogService.saveLog(commLog);
        } catch (Exception e) {
            log.error("仪器 [{}] 保存文件采集通讯日志失败", instrument.getId(), e);
        }
    }

    private List<Path> listCandidateFiles(String dir, Pattern pattern) {
        try (Stream<Path> stream = Files.list(Paths.get(dir))) {
            return stream
                    .filter(Files::isRegularFile)
                    .filter(p -> !p.getFileName().toString().startsWith("."))
                    .filter(p -> pattern == null || pattern.matcher(p.getFileName().toString()).find())
                    .sorted()
                    .toList();
        } catch (IOException e) {
            log.warn("读取文件目录失败: dir={}, err={}", dir, e.getMessage());
            return List.of();
        }
    }

    private Pattern compilePattern(String filePattern) {
        if (filePattern == null || filePattern.isBlank()) {
            return null;
        }
        try {
            return Pattern.compile(filePattern);
        } catch (Exception e) {
            log.warn("file_pattern 非法, 忽略: {}", filePattern);
            return null;
        }
    }
}