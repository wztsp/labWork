package com.lis.instrument.collector.file;

import com.lis.instrument.collector.support.ResultConstants;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * CSV / TXT 通用文件解析器
 * <p>
 * 支持两种布局:
 * <ul>
 *     <li><b>带表头</b>: 首行为列名, 通过列名映射到标准字段 (sampleNo/testCode/value/unit/refRange/abnormalFlag)</li>
 *     <li><b>固定列序</b>: 无表头时按第 1..N 列顺序映射到标准字段</li>
 * </ul>
 * 分隔符支持逗号、制表符、分号、竖线。
 */
@Slf4j
@Component
public class CsvFileParser implements FileParser {

    /** 固定列序映射: 第 1 列起依次为样本号/条码、检验项目代码、结果值、单位、参考范围、异常标志 */
    private static final String[] POSITIONAL_FIELDS = {
            ResultConstants.SAMPLE_NO,
            ResultConstants.TEST_CODE,
            ResultConstants.VALUE,
            ResultConstants.UNIT,
            ResultConstants.REF_RANGE,
            ResultConstants.ABNORMAL_FLAG
    };

    @Override
    public String getFormat() {
        return "CSV";
    }

    @Override
    public List<Map<String, String>> parse(String fileName, String fileContent) {
        List<Map<String, String>> results = new ArrayList<>();
        if (fileContent == null || fileContent.isBlank()) {
            return results;
        }
        String[] lines = fileContent.split("\r?\n");
        if (lines.length == 0) {
            return results;
        }

        List<String> header = null;
        int startLine = 0;
        String first = firstNonBlank(lines);
        if (first != null && isHeaderLine(first)) {
            header = split(first);
            startLine = indexOf(lines, first) + 1;
        }

        for (int i = startLine; i < lines.length; i++) {
            String line = lines[i].trim();
            if (line.isEmpty()) {
                continue;
            }
            List<String> cols = split(line);
            Map<String, String> row = header != null
                    ? mapByHeader(header, cols)
                    : mapByPosition(cols);
            if (hasUsableSampleAndCode(row)) {
                results.add(row);
            } else {
                log.debug("文件 [{}] 第 {} 行缺少样本号或检验项目, 跳过: {}", fileName, i + 1, line);
            }
        }
        return results;
    }

    /** 判断首行是否为表头 (包含常见标准字段名) */
    private boolean isHeaderLine(String line) {
        String lower = line.toLowerCase();
        return lower.contains("sampleno") || lower.contains("sample_no")
                || lower.contains("barcode") || lower.contains("testcode")
                || lower.contains("test_code") || lower.contains("item_code")
                || lower.contains("result_value") || lower.contains("resultvalue");
    }

    private String firstNonBlank(String[] lines) {
        for (String l : lines) {
            if (l != null && !l.trim().isEmpty()) {
                return l.trim();
            }
        }
        return null;
    }

    private int indexOf(String[] lines, String target) {
        for (int i = 0; i < lines.length; i++) {
            if (lines[i].trim().equals(target)) {
                return i;
            }
        }
        return -1;
    }

    private Map<String, String> mapByHeader(List<String> header, List<String> cols) {
        Map<String, String> row = new HashMap<>();
        for (int i = 0; i < header.size() && i < cols.size(); i++) {
            String key = normalizeHeader(header.get(i));
            if (key == null) {
                continue;
            }
            String value = cols.get(i).trim();
            if (!value.isEmpty()) {
                row.put(key, value);
            }
        }
        return row;
    }

    /** 将表头列名归一化为标准字段 key, 无法识别时返回 null */
    private String normalizeHeader(String raw) {
        if (raw == null) {
            return null;
        }
        String h = raw.trim().toLowerCase().replace(" ", "_");
        if (h.contains("sampleno") || h.contains("sample_no") || h.contains("barcode") || h.contains("seq")) {
            if (h.contains("barcode")) {
                return ResultConstants.SAMPLE_NO;
            }
            if (h.equals("seq")) {
                return ResultConstants.SEQ;
            }
            return ResultConstants.SAMPLE_NO;
        }
        if (h.contains("testcode") || h.contains("test_code") || h.contains("itemcode") || h.contains("item_code")) {
            return ResultConstants.TEST_CODE;
        }
        if (h.contains("resultvalue") || h.contains("result_value") || h.equals("value") || h.contains("数值")) {
            return ResultConstants.VALUE;
        }
        if (h.contains("unit") || h.contains("单位")) {
            return ResultConstants.UNIT;
        }
        if (h.contains("refrange") || h.contains("ref_range") || h.contains("reference")) {
            return ResultConstants.REF_RANGE;
        }
        if (h.contains("abnormal") || h.contains("flag") || h.contains("异常")) {
            return ResultConstants.ABNORMAL_FLAG;
        }
        return null;
    }

    private Map<String, String> mapByPosition(List<String> cols) {
        Map<String, String> row = new HashMap<>();
        for (int i = 0; i < cols.size() && i < POSITIONAL_FIELDS.length; i++) {
            String value = cols.get(i).trim();
            if (!value.isEmpty()) {
                row.put(POSITIONAL_FIELDS[i], value);
            }
        }
        return row;
    }

    private boolean hasUsableSampleAndCode(Map<String, String> row) {
        String sampleNo = row.get(ResultConstants.SAMPLE_NO);
        if ((sampleNo == null || sampleNo.isEmpty())
                && (row.get(ResultConstants.SEQ) == null || row.get(ResultConstants.SEQ).isEmpty())) {
            return false;
        }
        String code = row.get(ResultConstants.TEST_CODE);
        return code != null && !code.isEmpty();
    }

    /** 自动识别分隔符并拆分一行 */
    private List<String> split(String line) {
        char delim = ',';
        if (line.contains("\t")) {
            delim = '\t';
        } else if (line.contains(";")) {
            delim = ';';
        } else if (line.contains("|")) {
            delim = '|';
        }
        List<String> cols = new ArrayList<>();
        StringBuilder cur = new StringBuilder();
        boolean inQuote = false;
        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            if (c == '"') {
                inQuote = !inQuote;
                cur.append(c);
            } else if (c == delim && !inQuote) {
                cols.add(cur.toString().trim().replaceAll("^\"|\"$", ""));
                cur.setLength(0);
            } else {
                cur.append(c);
            }
        }
        cols.add(cur.toString().trim().replaceAll("^\"|\"$", ""));
        return cols;
    }
}