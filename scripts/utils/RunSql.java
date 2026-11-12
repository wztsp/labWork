import java.sql.*;
import java.nio.file.*;

public class RunSql {
    public static void main(String[] args) throws Exception {
        String url = "jdbc:postgresql://localhost:5432/lis_db";
        java.util.Properties props = new java.util.Properties();
        props.setProperty("user", "lis_user");
        props.setProperty("password", "lis_dev_2026");
        Connection conn = DriverManager.getConnection(url, props);

        String sql = Files.readString(Paths.get(args[0]));
        Statement stmt = conn.createStatement();
        stmt.execute(sql);
        System.out.println("SQL executed successfully");
        stmt.close();
        conn.close();
    }
}
