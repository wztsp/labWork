package com.lis.instrument.protocol.astm;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * ASTMParser 单元测试：解析标准 ASTM E1394 报文，并验证 isCriticalFlag 判定。
 */
class ASTMParserTest {

    private final ASTMParser parser = new ASTMParser();

    private static final String SAMPLE = String.join("\r\n",
            "H|\\^&|||psiv3^^^^|||LIS||||||P|1|20240101",
            "P|1||||||Smith^John||19800101|M|||||^||",
            "O|1|S12345^^^||^^^GLU|N|20240101120000||||||N||||||||||||||O",
            "R|1|^^^GLU|5.5|mmol/L|3.9-6.1|N||F|||||||||",
            "L|1|N");

    @Test
    void parse_extractsSampleNoAndResult() {
        List<Map<String, String>> results = parser.parse(SAMPLE);
        assertEquals(1, results.size());
        Map<String, String> r = results.get(0);
        assertEquals("S12345", r.get("sampleNo"));   // O 记录样本号透传
        assertEquals("GLU", r.get("testCode"));
        assertEquals("5.5", r.get("value"));
        assertEquals("mmol/L", r.get("unit"));
        assertEquals("3.9-6.1", r.get("refRange"));
        assertEquals("N", r.get("abnormalFlag"));
    }

    @Test
    void parse_emptyMessage_returnsEmpty() {
        assertTrue(parser.parse("").isEmpty());
        assertTrue(parser.parse(null).isEmpty());
    }

    @Test
    void parse_filtersControlCharacters() {
        // ENQ(0x05) 帧头应被过滤，不影响解析
        String withEnq = "\u0005" + SAMPLE;
        List<Map<String, String>> results = parser.parse(withEnq);
        assertEquals(1, results.size());
    }

    @Test
    void isCriticalFlag_judgement() {
        assertTrue(ASTMParser.isCriticalFlag("***"));      // 不可计算 -> 危急
        assertTrue(ASTMParser.isCriticalFlag(">5.0"));     // 超上限 -> 危急
        assertTrue(ASTMParser.isCriticalFlag("<1.0"));     // 超下限 -> 危急
        assertFalse(ASTMParser.isCriticalFlag("H"));       // 仅高标记 -> 非危急
        assertFalse(ASTMParser.isCriticalFlag("N"));
        assertFalse(ASTMParser.isCriticalFlag(null));
        assertNotNull(parser.getProtocolType());
    }
}
