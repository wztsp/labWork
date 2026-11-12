package com.lis.instrument.protocol.hl7;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * HL7Parser 单元测试：解析 HL7 v2 ORU^R01 报文（OBX 段）。
 */
class HL7ParserTest {

    private final HL7Parser parser = new HL7Parser();

    private static final String SAMPLE = String.join("\r\n",
            "MSH|^~\\&|INSTRUMENT|LAB|LIS|HOSP|20240101120000||ORU^R01|1|P|2.5",
            "PID|||P001||Smith^John||19800101|M",
            "OBR|1|S12345||^GLU|20240101120000",
            "OBX|1|NM|^^^GLU|1|5.5|mmol/L|3.5-6.1|N|||F");

    @Test
    void parse_extractsObxAndPatient() {
        List<Map<String, String>> results = parser.parse(SAMPLE);
        assertEquals(1, results.size());
        Map<String, String> r = results.get(0);
        assertEquals("GLU", r.get("testCode"));
        assertEquals("5.5", r.get("value"));
        assertEquals("mmol/L", r.get("unit"));
        assertEquals("3.5-6.1", r.get("refRange"));
        assertEquals("N", r.get("abnormalFlag"));
        assertEquals("P001", r.get("patientId"));
        assertEquals("Smith^John", r.get("patientName"));
    }

    @Test
    void parse_emptyMessage_returnsEmpty() {
        assertTrue(parser.parse("").isEmpty());
        assertTrue(parser.parse(null).isEmpty());
    }

    @Test
    void getProtocolType_isHL7() {
        assertEquals("HL7", parser.getProtocolType());
    }
}
