package com.lis.instrument.protocol.astm;

import com.lis.instrument.protocol.ProtocolParser;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * ASTM 协议解析器 (ASTM E1394 / E1381)
 * <p>
 * 标准消息示例 (迈瑞/希森美康等):
 * <pre>
 * H|\^&|||psiv3^^^^|||LIS||||||P|1|20240101
 * P|1||||||Smith^John||19800101|M|||||^||
 * O|1|S12345||^^^GLU|N|20240101120000||||||N||||||||||||||O
 * R|1|^^^GLU|5.5|mmol/L|3.9-6.1|N||F|||||||||
 * L|1|N
 * </pre>
 * 解析: H(头)/P(患者)/O(订单/样本)/R(结果)/L(结束) 记录。
 * <p>
 * O 记录格式: O|seq|样本号^^^||...  (字段2 为样本号/条码, 内部以 ^ 分隔多个分量)
 * R 记录格式: R|seq|^^^testCode|value|unit|refRange|abnormalFlag|...
 */
@Slf4j
@Component
public class ASTMParser implements ProtocolParser {

    @Override
    public String getProtocolType() {
        return "ASTM";
    }

    @Override
    public List<Map<String, String>> parse(String rawMessage) {
        List<Map<String, String>> results = new ArrayList<>();
        if (rawMessage == null || rawMessage.isEmpty()) {
            return results;
        }
        // 按行分割 (兼容 \r\n / \n / \r)
        String[] lines = rawMessage.split("\r?\n|\r");
        // 当前 O 记录解析出的样本号/条码, 透传到后续 R 记录
        String currentSampleNo = null;
        for (String line : lines) {
            if (line == null || line.isEmpty()) continue;
            // 过滤 ASTM 帧控制字符 (ENQ/EOT/ACK/NAK/STX 等)
            char first = line.charAt(0);
            if (first == 0x05 || first == 0x04 || first == 0x06 || first == 0x15 || first == 0x02) {
                continue;
            }
            char recordType = first;
            if (recordType == 'O') {
                // 解析 O 记录, 提取样本号/条码, 供后续 R 记录使用
                String sampleNo = parseOrderRecord(line);
                if (sampleNo != null && !sampleNo.isEmpty()) {
                    currentSampleNo = sampleNo;
                }
            } else if (recordType == 'R') {
                Map<String, String> r = parseResultRecord(line);
                if (r != null) {
                    // 透传样本号
                    if (currentSampleNo != null) {
                        r.put("sampleNo", currentSampleNo);
                    }
                    results.add(r);
                }
            }
        }
        log.debug("ASTM 解析完成: {} 条结果, sampleNo={}", results.size(), currentSampleNo);
        return results;
    }

    /**
     * 解析 O 记录 (订单/样本信息段)
     * O|seq|样本号^^^||...  -> 提取样本号/条码 (字段2 第一个分量)
     */
    private String parseOrderRecord(String line) {
        String[] fields = line.split("\\|", -1);
        if (fields.length < 3) {
            return null;
        }
        // fields[2] 形如 "S12345^^^" 或 "S12345^^^SampleType" 或 "12345"
        String sampleField = fields[2];
        if (sampleField == null || sampleField.isEmpty()) {
            // 某些仪器把样本号放在字段3
            if (fields.length > 3 && fields[3] != null && !fields[3].isEmpty()) {
                sampleField = fields[3];
            } else {
                return null;
            }
        }
        // 取 ^ 分隔的第一个非空分量作为样本号/条码
        String[] parts = sampleField.split("\\^", -1);
        for (String p : parts) {
            if (p != null && !p.isEmpty()) {
                return p.trim();
            }
        }
        return sampleField.trim();
    }

    /**
     * 解析 R 记录行 (完整字段)
     * 标准格式: R|seq|^^^testCode|value|unit|refRange|abnormalFlag|...
     *
     * 兼容手工编辑常见格式错误: 当 seq 和 ^^^testCode 之间漏写 | 时 (如 R|1^^^GLU|5.2|...),
     * 自动从 fields[1] 中拆分 seq 与 testCode, 并将其余字段左移一位对齐, 避免解析 value/code 错位。
     */
    private Map<String, String> parseResultRecord(String line) {
        String[] fields = line.split("\\|", -1);
        // ASTM R 记录至少 7 个字段
        if (fields.length < 7) {
            return null;
        }
        Map<String, String> r = new HashMap<>();
        r.put("recordType", "R");

        String seq = fields[1];
        String testField;
        String value, unit, refRange, abnormalFlag;

        // 检测格式错误: fields[1] 含有 ^ 字符 通常意味着漏写了 seq 与 testCode 之间的 |
        boolean missingPipe = (seq != null && seq.contains("^"));
        if (missingPipe) {
            // 形如 "1^^^GLU" → seq = "1", testField = "^^^GLU"
            int firstCaret = seq.indexOf('^');
            r.put("seq", seq.substring(0, firstCaret));
            testField = seq.substring(firstCaret);
            // 漏写 | 导致后续整体左移一位: value = fields[2], unit = fields[3] ...
            value      = fields.length > 2 ? fields[2] : null;
            unit       = fields.length > 3 ? fields[3] : null;
            refRange   = fields.length > 4 ? fields[4] : null;
            abnormalFlag = fields.length > 5 ? fields[5] : null;
            log.debug("ASTM R记录兼容漏写|分隔符: seq='{}' 拆分为 seq={} testField={}",
                    seq, r.get("seq"), testField);
        } else {
            // 标准格式
            r.put("seq", seq);
            testField = fields[2];
            value        = fields[3];
            unit         = fields[4];
            refRange     = fields[5];
            abnormalFlag = fields[6];
        }

        // fields[2]/testField 形如 "^^^GLU" → testCode=GLU (第4个分量)
        String[] testParts = (testField == null ? new String[0] : testField.split("\\^", -1));
        String testCode = testParts.length > 3 ? testParts[3] : testField;
        r.put("testCode", testCode);
        r.put("value", value);
        r.put("unit", unit);
        r.put("refRange", refRange);
        r.put("abnormalFlag", abnormalFlag);
        // R 记录可能有更多字段 (操作员/状态等), 容错解析
        int statusIdx = missingPipe ? 6 : 7;
        if (fields.length > statusIdx) {
            r.put("resultStatus", fields[statusIdx]);
        }
        return r;
    }

    /**
     * 判断异常标志是否为危急值
     * 规则: 包含 *** 或 > 或 < 标记视为危急值
     */
    public static boolean isCriticalFlag(String abnormalFlag) {
        if (abnormalFlag == null || abnormalFlag.isEmpty()) {
            return false;
        }
        String flag = abnormalFlag.trim().toUpperCase();
        // *** 表示结果超出线性范围/不可计算, 视为危急
        if (flag.contains("***")) {
            return true;
        }
        // >xxx / <xxx 表示超出测量范围
        if (flag.contains(">") || flag.contains("<")) {
            return true;
        }
        // HH/LL 表示极高/极低, 部分实验室视为危急 (保守: 仅 *** / > / < 触发危急)
        return false;
    }
}
