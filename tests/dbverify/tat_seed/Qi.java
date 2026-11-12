import java.sql.*;

public class Qi {
    public static void main(String[] a) throws Exception {
        try (Connection c = DriverManager.getConnection("jdbc:postgresql://localhost:5432/lis_db", "lis_user", "lis_dev_2026")) {
            String sql = "SELECT res.id, res.sample_id, res.test_item_id, res.result_value, res.abnormal_flag, res.audit_status, s.patient_name FROM lis_result res LEFT JOIN lis_sample s ON s.id=res.sample_id WHERE res.abnormal_flag IN ('H','L') ORDER BY res.id DESC LIMIT 5";
            try (Statement st = c.createStatement(); ResultSet rs = st.executeQuery(sql)) {
                while (rs.next()) {
                    System.out.println("id=" + rs.getLong(1) + " sample=" + rs.getLong(2) + " item=" + rs.getLong(3)
                            + " val=" + rs.getString(4) + " flag=" + rs.getString(5) + " audit=" + rs.getInt(6) + " pat=" + rs.getString(7));
                }
            }
        }
    }
}