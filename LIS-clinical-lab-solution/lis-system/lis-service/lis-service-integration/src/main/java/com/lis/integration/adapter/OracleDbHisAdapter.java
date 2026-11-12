package com.lis.integration.adapter;

import com.lis.api.dto.OrderDTO;
import com.lis.api.dto.OrderItemDTO;
import com.lis.integration.dto.CriticalValueAlert;
import com.lis.integration.dto.FeeBilling;
import com.lis.integration.dto.LabReport;
import com.lis.integration.dto.PatientInfo;
import com.lis.integration.dto.SampleStatusUpdate;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;

/**
 * HIS Oracle 数据库直连适配器
 * <p>
 * 通过 JDBC 直连 HIS 的 Oracle 库，按时间窗口增量读取检验申请，转换为 {@link OrderDTO}。
 * 无需 HIS 开发接口，适用「HIS 是 Oracle」且不便开放 WebService 的场景。
 * <p>
 * 配置项 (application.yml)：
 * <pre>
 * integration:
 *   his:
 *     default-vendor: ORACLE_DB     # 切换数据源为数据库直连
 *     vendors:
 *       ORACLE_DB:
 *         jdbc-url: jdbc:oracle:thin:@//host:1521/sid
 *         username: lis_reader
 *         password: ${HIS_ORACLE_PWD:xxxx}
 *         driver: oracle.jdbc.OracleDriver
 *         # HIS 申请主表 SQL (占位符 ?1=startTime, ?2=endTime, 用时间字段过滤增量)
 *         order-sql: "SELECT ORDER_NO, PATIENT_ID, PATIENT_NAME, ORDER_TYPE, APPLY_DOCTOR, APPLY_DEPT, CLINICAL_DIAG, APPLY_TIME FROM HIS_ORDER WHERE APPLY_TIME >= ?1 AND APPLY_TIME < ?2"
 *         # HIS 申请明细 (项目) SQL (占位符 ?1=orderNo)
 *         item-sql: "SELECT TEST_ITEM_CODE, TEST_ITEM_NAME, FEE FROM HIS_ORDER_ITEM WHERE ORDER_NO = ?1"
 * </pre>
 */
@Slf4j
@Component
public class OracleDbHisAdapter implements HisAdapter {

    @Value("${integration.his.vendors.ORACLE_DB.jdbc-url:}")
    private String jdbcUrl;
    @Value("${integration.his.vendors.ORACLE_DB.username:}")
    private String username;
    @Value("${integration.his.vendors.ORACLE_DB.password:}")
    private String password;
    @Value("${integration.his.vendors.ORACLE_DB.driver:oracle.jdbc.OracleDriver}")
    private String driver;
    @Value("${integration.his.vendors.ORACLE_DB.order-sql:}")
    private String orderSql;
    @Value("${integration.his.vendors.ORACLE_DB.item-sql:}")
    private String itemSql;
    @Value("${his.enabled:false}")
    private boolean hisEnabled;

    @Override
    public String getAdapterId() {
        return "ORACLE_DB";
    }

    private Connection open() throws Exception {
        Class.forName(driver);
        return DriverManager.getConnection(jdbcUrl, username, password);
    }

    @Override
    public boolean healthCheck() {
        if (!hisEnabled || jdbcUrl.isEmpty()) {
            log.debug("[OracleDbHisAdapter] 未启用或未配置, 跳过健康检查");
            return false;
        }
        try (Connection c = open(); PreparedStatement ps = c.prepareStatement("SELECT 1 FROM DUAL");
             ResultSet rs = ps.executeQuery()) {
            boolean ok = rs.next();
            log.info("[OracleDbHisAdapter] 健康检查: {}", ok ? "成功" : "失败");
            return ok;
        } catch (Exception e) {
            log.warn("[OracleDbHisAdapter] 健康检查失败: {}", e.getMessage());
            return false;
        }
    }

    @Override
    public List<OrderDTO> receiveOrders(Date startTime, Date endTime) {
        if (!hisEnabled || orderSql == null || orderSql.isEmpty()) {
            log.debug("[OracleDbHisAdapter] 未启用或未配置 order-sql, 跳过申请同步");
            return Collections.emptyList();
        }
        List<OrderDTO> result = new ArrayList<>();
        try (Connection c = open();
             PreparedStatement ps = buildOrderPs(c, startTime, endTime);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                OrderDTO order = mapOrder(rs);
                if (order != null && order.getOrderNo() != null) {
                    order.setItems(queryItems(c, order.getOrderNo()));
                    result.add(order);
                }
            }
            log.info("[OracleDbHisAdapter] 从 HIS Oracle 读取到 {} 条申请", result.size());
            return result;
        } catch (Exception e) {
            log.error("[OracleDbHisAdapter] 读取 HIS 申请失败", e);
            return Collections.emptyList();
        }
    }

    private PreparedStatement buildOrderPs(Connection c, Date start, Date end) throws Exception {
        PreparedStatement ps = c.prepareStatement(orderSql);
        ps.setTimestamp(1, new Timestamp(start.getTime()));
        ps.setTimestamp(2, new Timestamp(end.getTime()));
        return ps;
    }

    private OrderDTO mapOrder(ResultSet rs) throws Exception {
        OrderDTO.OrderDTOBuilder b = OrderDTO.builder()
                .orderNo(rs.getString("ORDER_NO"))
                .patientName(text(rs, "PATIENT_NAME"));
        Long pid = longOr(rs, "PATIENT_ID");
        Long vid = longOr(rs, "VISIT_ID");
        if (pid != null) b.patientId(pid);
        if (vid != null) b.visitId(vid);
        b.orderType(text(rs, "ORDER_TYPE"));
        b.applyDoctor(text(rs, "APPLY_DOCTOR"));
        b.applyDept(text(rs, "APPLY_DEPT"));
        b.clinicalDiag(text(rs, "CLINICAL_DIAG"));
        b.patientCondition(text(rs, "PATIENT_CONDITION"));
        Long cmid = longOr(rs, "COLLECTION_METHOD_ID");
        if (cmid != null) b.collectionMethodId(cmid);
        b.collectionMethodName(text(rs, "COLLECTION_METHOD_NAME"));
        return b.build();
    }

    private List<OrderItemDTO> queryItems(Connection c, String orderNo) {
        if (itemSql == null || itemSql.isEmpty()) {
            return new ArrayList<>();
        }
        List<OrderItemDTO> items = new ArrayList<>();
        try (PreparedStatement ps = c.prepareStatement(itemSql)) {
            ps.setString(1, orderNo);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    OrderItemDTO.OrderItemDTOBuilder ib = OrderItemDTO.builder()
                            .testItemCode(rs.getString("TEST_ITEM_CODE"))
                            .testItemName(rs.getString("TEST_ITEM_NAME"));
                    BigDecimal fee = rs.getBigDecimal("FEE");
                    if (fee != null) ib.fee(fee);
                    items.add(ib.build());
                }
            }
        } catch (Exception e) {
            log.warn("[OracleDbHisAdapter] 读取申请项目失败 orderNo={}: {}", orderNo, e.getMessage());
        }
        return items;
    }

    private String text(ResultSet rs, String col) {
        try {
            return rs.getString(col);
        } catch (Exception e) {
            return null;
        }
    }

    private Long longOr(ResultSet rs, String col) {
        try {
            BigDecimal v = rs.getBigDecimal(col);
            return v == null ? null : v.longValue();
        } catch (Exception e) {
            return null;
        }
    }

    @Override
    public PatientInfo queryPatient(String patientId, String visitId) {
        return null;
    }

    @Override
    public boolean sendResults(LabReport report) {
        return false;
    }

    @Override
    public boolean sendSampleStatus(SampleStatusUpdate update) {
        return false;
    }

    @Override
    public boolean sendCriticalValueAlert(CriticalValueAlert alert) {
        return false;
    }

    @Override
    public boolean sendFeeBilling(FeeBilling billing) {
        return false;
    }
}