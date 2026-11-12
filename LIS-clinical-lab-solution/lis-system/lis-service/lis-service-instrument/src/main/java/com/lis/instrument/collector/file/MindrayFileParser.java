package com.lis.instrument.collector.file;

import com.lis.instrument.collector.support.ResultConstants;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 厂商私有格式解析器示例 (迈瑞类仪器结果导出文件)
 * <p>
 * 典型格式: 制表符分隔, 首行为表头, 列名含中文 (样本号/条码, 项目, 结果, 单位, 参考范围, 异常提示)。
 * <pre>
 * 样本号	项目		结果	单位	参考范围	提示
 * 20260812001	WBC	6.8	10^9/L	3.5-9.5	H
 * </pre>
 * 通过表头中文/英文名映射到标准字段。可按同等方式新增其它厂商解析器。
 */
@Slf4j
@Component
public class MindrayFileParser implements FileParser {

    @Override
    public String getFormat() {
        return "MINDRAY";
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

        // 定位表头行 (含"样本"或"项目"或"结果"等关键词)
        int headerIdx = -1;
        List<String> header = null;
        for (int i = 0; i < lines.length; i++) {
            String line = lines[i];
            if (line.contains("样本") || line.contains("样本号") || line.contains("条码")
                    || line.toLowerCase().contains("sampleno")) {
                header = split(line);
                headerIdx = i;
                break;
            }
        }
        if (header == null) {
            return results;
        }

        for (int i = headerIdx + 1; i < lines.length; i++) {
            String line = lines[i].trim();
            if (line.isEmpty()) {
                continue;
            }
            List<String> cols = split(line);
            Map<String, String> row = new HashMap<>();
            for (int c = 0; c < header.size() && c < cols.size(); c++) {
                String key = mapColumn(header.get(c));
                if (key == null) {
                    continue;
                }
                String value = cols.get(c).trim();
                if (!value.isEmpty()) {
                    row.put(key, value);
                }
            }
            // 跳过表头后的空表头行/分隔行
            String sampleNo = row.get(ResultConstants.SAMPLE_NO);
            String code = row.get(ResultConstants.TEST_CODE);
            String value = row.get(ResultConstants.VALUE);
            if ((sampleNo == null || sampleNo.isEmpty()) && (row.get(ResultConstants.SEQ) == null)) {
                continue;
            }
            if ((code == null || code.isEmpty()) && (value == null || value.isEmpty())) {
                continue;
            }
            results.add(row);
        }
        return results;
    }

    /** 将来源列名映射为标准字段, 无法识别返回 null */
    private String mapColumn(String raw) {
        if (raw == null) {
            return null;
        }
        String h = raw.trim().toLowerCase().replace(" ", "");
        if (h.contains("样本号") || h.contains("条码") || h.contains("sampleno") || h.contains("barcode")) {
            return ResultConstants.SAMPLE_NO;
        }
        if (h.contains("项目") || h.contains("testcode") || h.contains("itemcode") || h.contains("testcode")) {
            return ResultConstants.TEST_CODE;
        }
        if (h.equals("结果") || h.contains("result") || h.contains("数值")) {
            return ResultConstants.VALUE;
        }
        if (h.contains("单位") || h.contains("unit")) {
            return ResultConstants.UNIT;
        }
        if (h.contains("参考") || h.contains("ref")) {
            return ResultConstants.REF_RANGE;
        }
        if (h.contains("提示") || h.contains("异常") || h.contains("flag") || h.contains("abnormal")) {
            return ResultConstants.ABNORMAL_FLAG;
        }
        return null;
    }

    private List<String> split(String line) {
        char delim = '\t';
        if (!line.contains("\t")) {
            delim = ',';
        }
        List<String> cols = new ArrayList<>();
        StringBuilder cur = new StringBuilder();
        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            if (c == delim) {
                cols.add(cur.toString().trim());
                cur.setLength(0);
            } else {
                cur.append(c);
            }
        }
        cols.add(cur.toString().trim());
        return cols;
    }
}