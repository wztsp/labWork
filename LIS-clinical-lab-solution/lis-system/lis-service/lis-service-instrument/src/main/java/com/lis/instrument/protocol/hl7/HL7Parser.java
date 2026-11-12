package com.lis.instrument.protocol.hl7;

import com.lis.instrument.protocol.ProtocolParser;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * HL7 v2 协议解析器 (简化实现: 按 | 和 ^ 分隔)
 * <p>
 * HL7 ORU^R01 消息示例 (罗氏/雅培等):
 * <pre>
 * MSH|^~\&|INSTRUMENT|LAB|LIS|HOSP|20240101120000||ORU^R01|1|P|2.5
 * PID|||P001||Smith^John||19800101|M
 * OBR|1|S12345||^GLU|20240101120000
 * OBX|1|NM|^^^GLU|1|5.5|mmol/L|3.5-6.1|N|||F
 * </pre>
 * 简化处理: 仅解析 OBX 段的结果记录。
 */
@Slf4j
@Component
public class HL7Parser implements ProtocolParser {

    @Override
    public String getProtocolType() {
        return "HL7";
    }

    @Override
    public List<Map<String, String>> parse(String rawMessage) {
        List<Map<String, String>> results = new ArrayList<>();
        if (rawMessage == null || rawMessage.isEmpty()) {
            return results;
        }
        String[] segments = rawMessage.split("\r?\n");
        // 先提取患者ID/姓名 (PID 段)
        String patientId = null;
        String patientName = null;
        for (String seg : segments) {
            if (seg == null || seg.isEmpty()) continue;
            if (seg.startsWith("PID|")) {
                String[] fields = seg.split("\\|", -1);
                if (fields.length > 3) {
                    patientId = fields[3];
                }
                if (fields.length > 5) {
                    patientName = fields[5];
                }
            }
        }
        // 解析 OBX 段
        for (String seg : segments) {
            if (seg == null || seg.isEmpty()) continue;
            if (seg.startsWith("OBX|")) {
                Map<String, String> r = parseObx(seg);
                if (r != null) {
                    if (patientId != null) r.put("patientId", patientId);
                    if (patientName != null) r.put("patientName", patientName);
                    results.add(r);
                }
            }
        }
        log.debug("HL7 解析完成: {} 条结果", results.size());
        return results;
    }

    /**
     * 解析 OBX 段
     */
    private Map<String, String> parseObx(String seg) {
        // OBX | seq | type | ^^^testCode | subSeq | value | unit | refRange | abnormalFlag | ... | status
        String[] fields = seg.split("\\|", -1);
        if (fields.length < 8) {
            return null;
        }
        Map<String, String> r = new HashMap<>();
        r.put("segmentType", "OBX");
        r.put("seq", fields[1]);
        r.put("valueType", fields[2]);
        // fields[3] 形如 "^^^GLU" → testCode=GLU
        String[] testParts = fields[3].split("\\^", -1);
        String testCode = testParts.length > 3 ? testParts[3] : fields[3];
        r.put("testCode", testCode);
        r.put("subSeq", fields[4]);
        r.put("value", fields[5]);
        r.put("unit", fields[6]);
        r.put("refRange", fields[7]);
        if (fields.length > 8) r.put("abnormalFlag", fields[8]);
        if (fields.length > 11) r.put("status", fields[11]);
        return r;
    }
}
