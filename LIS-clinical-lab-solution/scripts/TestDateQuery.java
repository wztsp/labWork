import java.sql.*;
public class TestDateQuery {
    public static void main(String[] args) throws Exception {
        String url = "jdbc:postgresql://localhost:5432/lis_db";
        String user = "lis_user";
        String password = "lis_dev_2026";
        Class.forName("org.postgresql.Driver");
        try (Connection conn = DriverManager.getConnection(url, user, password)) {
            // 方式1: 直接 setString
            String sql = "SELECT COUNT(*) FROM lis_report r WHERE COALESCE(r.publish_time, r.create_time) >= ? AND COALESCE(r.publish_time, r.create_time) <= ?";
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, "2026-08-01 00:00:00");
                ps.setString(2, "2026-08-31 23:59:59");
                try (ResultSet rs = ps.executeQuery()) {
                    rs.next();
                    System.out.println("setString: count=" + rs.getInt(1));
                }
            }
            // 方式2: setTimestamp
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setTimestamp(1, java.sql.Timestamp.valueOf("2026-08-01 00:00:00"));
                ps.setTimestamp(2, java.sql.Timestamp.valueOf("2026-08-31 23:59:59"));
                try (ResultSet rs = ps.executeQuery()) {
                    rs.next();
                    System.out.println("setTimestamp: count=" + rs.getInt(1));
                }
            }
            // 查看原始数据类型
            String sql2 = "SELECT id, create_time, pg_typeof(create_time) as ct_type, publish_time, pg_typeof(publish_time) as pt_type FROM lis_report WHERE id = 300";
            try (Statement s = conn.createStatement(); ResultSet rs = s.executeQuery(sql2)) {
                if (rs.next()) {
                    System.out.println("id=300 create_time=" + rs.getString("create_time") 
                        + " type=" + rs.getString("ct_type") 
                        + " publish_time=" + rs.getString("publish_time") 
                        + " type=" + rs.getString("pt_type"));
                }
            }
        }
    }
}
