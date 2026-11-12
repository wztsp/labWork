import java.sql.*;

public class VerifyResultCols {
    public static void main(String[] args) throws Exception {
        String url = "jdbc:postgresql://localhost:5432/lis_db";
        java.util.Properties props = new java.util.Properties();
        props.setProperty("user", "lis_user");
        props.setProperty("password", "lis_dev_2026");
        try (Connection conn = DriverManager.getConnection(url, props);
             Statement stmt = conn.createStatement()) {
            ResultSet rs = stmt.executeQuery("""
                    SELECT column_name, data_type, udt_name
                    FROM information_schema.columns
                    WHERE table_name = 'lis_result'
                    ORDER BY ordinal_position
                    """);
            System.out.println("=== lis_result columns ===");
            while (rs.next()) {
                System.out.printf("  %-25s %-20s %s%n",
                        rs.getString("column_name"),
                        rs.getString("data_type"),
                        rs.getString("udt_name"));
            }
            rs.close();
        }
    }
}
