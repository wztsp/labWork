import java.sql.*;

public class MsgSeed {
    public static void main(String[] a) throws Exception {
        try (Connection c = DriverManager.getConnection("jdbc:postgresql://localhost:5432/lis_db", "lis_user", "lis_dev_2026")) {
            // 清理旧测试消息
            try (Statement st = c.createStatement()) {
                st.executeUpdate("DELETE FROM lis_message WHERE title IN ('危急值告警','TAT超时提醒-测试')");
            }
            String sql = "INSERT INTO lis_message (message_type, title, content, user_id, department_id, business_id, sample_id, test_item_id, is_read, create_time) "
                    + "VALUES ('CRITICAL', '危急值告警', '患者 测试患者 检验项目 HGB 结果 8.9g/L 为危急值, 请立即处理!', NULL, NULL, 212, 203, 2, 0, now())";
            try (Statement st = c.createStatement()) {
                st.executeUpdate(sql);
            }
            System.out.println("INSERTED CRITICAL message");
        }
    }
}