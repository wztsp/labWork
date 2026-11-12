import java.sql.*;
import java.time.LocalDateTime;

public class TatSeed {
    public static void main(String[] args) throws Exception {
        String url = "jdbc:postgresql://localhost:5432/lis_db";
        String user = "lis_user";
        String pass = "lis_dev_2026";
        try (Connection c = DriverManager.getConnection(url, user, pass)) {
            // 清理旧的测试数据
            try (Statement st = c.createStatement()) {
                st.executeUpdate("DELETE FROM lis_sample WHERE sample_barcode LIKE 'TAT-%'");
            }
            long sampleId = System.currentTimeMillis();
            String barcode = "TAT-" + (sampleId % 1000000);
            Timestamp signOff = Timestamp.valueOf(LocalDateTime.now().minusHours(2));
            String sql = "INSERT INTO lis_sample (id, order_id, visit_id, patient_id, sample_barcode, sample_no, patient_name, sample_type, status, is_urgent, sign_off_time, operator_id, department, deleted, create_time, update_time) "
                    + "VALUES (?, 1, 1, 1, ?, ?, ?, 'SERUM', 3, 0, ?, 1, '检验科', 0, now(), now())";
            try (PreparedStatement ps = c.prepareStatement(sql)) {
                ps.setLong(1, sampleId);
                ps.setString(2, barcode);
                ps.setString(3, "TAT01");
                ps.setString(4, "TAT验证患者");
                ps.setTimestamp(5, signOff);
                ps.executeUpdate();
            }
            String sql2 = "INSERT INTO lis_sample_order_item (id, sample_id, order_id, order_item_id, test_item_id, create_time) VALUES (?, ?, 1, 1, 79, now())";
            try (PreparedStatement ps = c.prepareStatement(sql2)) {
                ps.setLong(1, sampleId + 1);
                ps.setLong(2, sampleId);
                ps.executeUpdate();
            }
            System.out.println("INSERTED sampleId=" + sampleId + " barcode=" + barcode + " K(79)/SERUM signOff=" + signOff);
        }
    }
}