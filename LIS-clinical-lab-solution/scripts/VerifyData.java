import java.sql.*;

public class VerifyData {
    public static void main(String[] args) throws Exception {
        String url = "jdbc:postgresql://localhost:5432/lis_db";
        String user = "lis_user";
        String password = "lis_dev_2026";

        Class.forName("org.postgresql.Driver");
        try (Connection conn = DriverManager.getConnection(url, user, password)) {
            System.out.println("=== 待审核报告 (status=0) ===");
            try (Statement s = conn.createStatement()) {
                ResultSet rs = s.executeQuery("SELECT r.id, r.report_no, r.patient_name, r.status, r.create_time, p.phone_enc, v.department, l.clinical_diag FROM lis_report r LEFT JOIN lis_patient p ON r.patient_id = p.id LEFT JOIN lis_sample s2 ON r.sample_id = s2.id LEFT JOIN lis_visit v ON s2.visit_id = v.id LEFT JOIN lis_lab_order l ON s2.order_id = l.id WHERE r.status = 0 ORDER BY r.id");
                while (rs.next()) {
                    System.out.println("  ID:" + rs.getLong("id") + " | " + rs.getString("report_no") + " | 患者:" + rs.getString("patient_name") + " | 科室:" + rs.getString("department") + " | 诊断:" + rs.getString("clinical_diag"));
                }
            }
            System.out.println();
            System.out.println("=== 统计 ===");
            String[] tables = {"lis_patient", "lis_visit", "lis_lab_order", "lis_order_item", "lis_sample", "lis_result", "lis_report"};
            for (String t : tables) {
                try (Statement s = conn.createStatement()) {
                    ResultSet rs = s.executeQuery("SELECT COUNT(*) FROM " + t);
                    rs.next();
                    System.out.println(t + ": " + rs.getLong(1) + " rows");
                }
            }
        }
    }
}
