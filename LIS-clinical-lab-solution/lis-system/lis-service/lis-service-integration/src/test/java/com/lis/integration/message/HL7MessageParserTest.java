package com.lis.integration.message;

import com.lis.api.dto.OrderDTO;
import com.lis.integration.dto.PatientInfo;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * HL7MessageParser 单元测试：ORM^O01 / ADT^A01 / RSP^K22 三种消息解析。
 * 不依赖 Spring，直接 new 实例。
 */
class HL7MessageParserTest {

    private final HL7MessageParser parser = new HL7MessageParser();

    // ---------------- ORM^O01 检验申请 ----------------
    @Test
    void parseORM_O01_extractsOrder() {
        String msg = String.join("\r\n",
                "MSH|^~\\&|HIS|LAB|LIS|HOSP|20240101120000||ORM^O01|1|P|2.5",
                "PID|||1001||Smith^John||19800101|M",
                "OBR|1|ORD123||^GLU^Glucose|20240101120000",
                "PV1||O|DEPT01");
        OrderDTO order = parser.parseORM_O01(msg);
        assertNotNull(order);
        assertEquals("ORD123", order.getOrderNo());
        assertEquals(1001L, order.getPatientId());
        assertEquals("Smith^John", order.getPatientName());
        assertEquals("DEPT01", order.getApplyDept());
        assertEquals(1, order.getItems().size());
        assertEquals("GLU", order.getItems().get(0).getTestItemCode());
        assertEquals("Glucose", order.getItems().get(0).getTestItemName());
    }

    @Test
    void parseORM_O01_nullOrBlank() {
        assertNull(parser.parseORM_O01(null));
        assertNull(parser.parseORM_O01("   "));
    }

    // ---------------- ADT^A01 患者信息 ----------------
    @Test
    void parseADT_A01_extractsPatient() {
        // PID 字段索引: 3=patientId 5=name 6=birth 7=gender 13=phone 19=address
        String pid = "PID|||1001||Smith^John|19800101|M||||||13800000000||||||ADDR1";
        String pv1 = "PV1|1|O|DEPT01|BED01||||DOC1";
        String msg = String.join("\r\n",
                "MSH|^~\\&|HIS|LAB|LIS|HOSP|20240101120000||ADT^A01|1|P|2.5",
                pid, pv1);
        PatientInfo info = parser.parseADT_A01(msg);
        assertNotNull(info);
        assertEquals("1001", info.getPatientId());
        assertEquals("Smith^John", info.getPatientName());
        assertEquals("M", info.getGender());
        assertEquals(LocalDate.of(1980, 1, 1), info.getBirthday());
        assertEquals("13800000000", info.getPhone());
        assertEquals("ADDR1", info.getAddress());
        assertEquals("O", info.getVisitType());
        assertEquals("DEPT01", info.getDepartment());
        assertEquals("BED01", info.getBedNo());
        assertEquals("DOC1", info.getDoctorName());
    }

    @Test
    void parseADT_A01_nullOrBlank() {
        assertNull(parser.parseADT_A01(null));
        assertNull(parser.parseADT_A01(""));
    }

    // ---------------- RSP^K22 申请查询响应 ----------------
    @Test
    void parseRSP_K22_multipleOrders() {
        String msg = String.join("\r\n",
                "MSH|^~\\&|HIS|LAB|LIS|HOSP|20240101120000||RSP^K22|1|P|2.5",
                "PID|||1001||Smith^John",
                "OBR|1|ORD1||^GLU",
                "PV1||O|DEPT01",
                "OBR|2|ORD2||^ALT");
        List<OrderDTO> orders = parser.parseRSP_K22(msg);
        assertEquals(2, orders.size());
        assertEquals("ORD1", orders.get(0).getOrderNo());
        assertEquals("ORD2", orders.get(1).getOrderNo());
        assertTrue(orders.get(0).getItems().size() >= 1);
    }

    @Test
    void parseRSP_K22_nullOrBlank() {
        assertTrue(parser.parseRSP_K22(null).isEmpty());
        assertTrue(parser.parseRSP_K22("  ").isEmpty());
    }
}
