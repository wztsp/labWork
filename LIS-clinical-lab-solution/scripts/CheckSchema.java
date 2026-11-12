import java.sql.*;

public class CheckSchema {
    public static void main(String[] args) throws Exception {
        String url = "jdbc:postgresql://localhost:5432/lis_db";
        String user = "lis_user";
        String password = "lis_dev_2026";

        Class.forName("org.postgresql.Driver");
        try (Connection conn = DriverManager.getConnection(url, user, password)) {
            String[] tables = {"lis_patient", "lis_visit", "lis_lab_order", "lis_order_item", "lis_sample", "lis_result", "lis_report"};
            for (String t : tables) {
                System.out.println("=== " + t + " ===");
                try (ResultSet rs = conn.getMetaData().getColumns(null, null, t, null)) {
                    while (rs.next()) {
                        System.out.println("  " + rs.getString("COLUMN_NAME") + " (" + rs.getString("TYPE_NAME") + ")");
                    }
                }
                System.out.println();
            }
        }
    }
}
