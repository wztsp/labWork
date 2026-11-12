import java.sql.*;

public class QuerySampleImages {
    public static void main(String[] args) throws Exception {
        String url = "jdbc:postgresql://localhost:5432/lis_db";
        String user = "lis_user";
        String pass = "lis_dev_2026";
        try (Connection conn = DriverManager.getConnection(url, user, pass)) {
            System.out.println("=== lis_sample_image rows (sample_id, count) ===");
            try (Statement st = conn.createStatement();
                 ResultSet rs = st.executeQuery(
                   "SELECT si.sample_id, count(*) c, max(si.image_name) nm " +
                   "FROM lis_sample_image si GROUP BY si.sample_id ORDER BY c DESC")) {
                while (rs.next()) {
                    long sid = rs.getLong("sample_id");
                    int c = rs.getInt("c");
                    String name = rs.getString("nm");
                    System.out.println("sample_id=" + sid + " count=" + c + " sample=" + name);
                }
            }
            System.out.println("=== lis_report sample_id list ===");
            try (Statement st = conn.createStatement();
                 ResultSet rs = st.executeQuery(
                   "SELECT sample_id, report_no FROM lis_report ORDER BY id DESC LIMIT 20")) {
                while (rs.next()) {
                    System.out.println("sample_id=" + rs.getLong("sample_id") + " report_no=" + rs.getString("report_no"));
                }
            }
        }
    }
}