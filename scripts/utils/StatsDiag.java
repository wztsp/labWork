import java.sql.*;

public class StatsDiag {
    public static void main(String[] args) throws Exception {
        String url = "jdbc:postgresql://localhost:5432/lis_db";
        try (Connection c = DriverManager.getConnection(url, "lis_user", "lis_dev_2026")) {
            q(c, "== sample status all ==", "SELECT status, COUNT(*) cnt FROM lis_sample GROUP BY status ORDER BY status");
            q(c, "== sample status excl test ==", "SELECT s.status, COUNT(*) cnt FROM lis_sample s WHERE NOT EXISTS (SELECT 1 FROM lis_patient p WHERE p.id=s.patient_id AND p.is_test=1) GROUP BY s.status ORDER BY s.status");
            q(c, "== test patients ==", "SELECT id, name, is_test FROM lis_patient WHERE is_test=1");
            q(c, "== result is_critical dist ==", "SELECT is_critical, COUNT(*) cnt FROM lis_result GROUP BY is_critical");
            q(c, "== critical result audit ==", "SELECT audit_status, COUNT(*) cnt FROM lis_result WHERE is_critical=1 GROUP BY audit_status");
            q(c, "== abnormal_flag dist ==", "SELECT abnormal_flag, COUNT(*) cnt FROM lis_result GROUP BY abnormal_flag ORDER BY abnormal_flag");
            q(c, "== order_item summary ==", "SELECT COUNT(*) item_cnt, COUNT(DISTINCT order_id) ord_cnt, SUM(fee) total_fee FROM lis_order_item");
            q(c, "== lab_order status ==", "SELECT status, COUNT(*) cnt FROM lis_lab_order GROUP BY status");
            q(c, "== total excl test ==", "SELECT COUNT(*) total FROM lis_sample s WHERE NOT EXISTS (SELECT 1 FROM lis_patient p WHERE p.id=s.patient_id AND p.is_test=1)");
            q(c, "== reported st6 excl test ==", "SELECT COUNT(*) FROM lis_sample s WHERE s.status=6 AND NOT EXISTS (SELECT 1 FROM lis_patient p WHERE p.id=s.patient_id AND p.is_test=1)");
            q(c, "== critical record table ==", "SELECT COUNT(*) FROM lis_critical_record");
            q(c, "== result count excl test ==", "SELECT COUNT(*) FROM lis_result r LEFT JOIN lis_sample s ON r.sample_id=s.id WHERE NOT EXISTS (SELECT 1 FROM lis_patient p WHERE p.id=s.patient_id AND p.is_test=1) OR s.patient_id IS NULL");
        }
    }
    static void q(Connection c, String title, String sql) throws Exception {
        System.out.println(title);
        try (Statement st = c.createStatement(); ResultSet rs = st.executeQuery(sql)) {
            ResultSetMetaData md = rs.getMetaData();
            int n = md.getColumnCount();
            while (rs.next()) {
                StringBuilder sb = new StringBuilder();
                for (int i = 1; i <= n; i++) {
                    if (i > 1) sb.append(" | ");
                    sb.append(md.getColumnLabel(i)).append("=").append(rs.getString(i));
                }
                System.out.println("  " + sb);
            }
        }
        System.out.println();
    }
}