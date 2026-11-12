import java.sql.*;

public class CheckData {
    public static void main(String[] args) throws Exception {
        String url = "jdbc:postgresql://localhost:5432/lis_db";
        String user = "lis_user";
        String password = "lis_dev_2026";

        Class.forName("org.postgresql.Driver");
        try (Connection conn = DriverManager.getConnection(url, user, password)) {
            String[] tables = {"lis_patient", "lis_visit", "lis_lab_order", "lis_order_item", "lis_sample", "lis_result", "lis_report"};
            for (String t : tables) {
                try (Statement s = conn.createStatement()) {
                    ResultSet rs = s.executeQuery("SELECT id, COUNT(*) FROM " + t + " GROUP BY id ORDER BY id");
                    int count = 0;
                    int maxId = 0;
                    while (rs.next()) {
                        count++;
                        maxId = Math.max(maxId, rs.getInt(1));
                    }
                    System.out.println(t + ": " + count + " rows, max ID = " + maxId);
                }
            }
            System.out.println("\n--- Checking pending reports (status=0) ---");
            try (Statement s = conn.createStatement()) {
                ResultSet rs = s.executeQuery("SELECT id, report_no, patient_name, status, create_time FROM lis_report WHERE status = 0");
                while (rs.next()) {
                    System.out.println("  " + rs.getString("id") + " | " + rs.getString("report_no") + " | " + rs.getString("patient_name") + " | status=" + rs.getInt("status"));
                }
            }
        }
    }
}
