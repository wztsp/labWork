import java.sql.*;
import java.nio.file.*;
import java.util.*;
import java.util.regex.*;

public class InsertAuditData {
    public static void main(String[] args) throws Exception {
        String url = "jdbc:postgresql://localhost:5432/lis_db";
        String user = "lis_user";
        String password = "lis_dev_2026";

        String raw = new String(Files.readAllBytes(Paths.get(args[0])));

        Class.forName("org.postgresql.Driver");
        try (Connection conn = DriverManager.getConnection(url, user, password)) {
            conn.setAutoCommit(false);

            String sql = raw.replaceAll("--[^\\n]*", "").replaceAll("/\\*.*?\\*/", "");
            String[] parts = sql.split(";");

            int total = 0;
            List<String> errors = new ArrayList<>();

            for (String part : parts) {
                String stmt = part.trim();
                if (stmt.isEmpty()) continue;
                if (stmt.equalsIgnoreCase("BEGIN") || stmt.equalsIgnoreCase("COMMIT")) continue;

                try (Statement s = conn.createStatement()) {
                    if (stmt.toLowerCase().startsWith("select")) {
                        try (ResultSet rs = s.executeQuery(stmt)) {
                            while (rs.next()) { /* consume */ }
                        }
                    } else {
                        int rows = s.executeUpdate(stmt);
                        total += rows;
                        String pv = stmt.replaceAll("\\s+", " ").trim();
                        if (pv.length() > 120) pv = pv.substring(0, 120) + "...";
                        System.out.println("OK (" + rows + " rows): " + pv);
                    }
                } catch (Exception e) {
                    errors.add("ERROR: " + e.getMessage() + "  SQL: " + stmt.substring(0, Math.min(100, stmt.length())));
                    System.err.println("ERROR: " + e.getMessage());
                }
            }

            if (errors.isEmpty()) {
                conn.commit();
                System.out.println("\n=== SUCCESS === Total rows: " + total);
            } else {
                conn.rollback();
                System.out.println("\n=== ROLLED BACK ===");
                for (String err : errors) System.err.println(err);
            }
        }
    }
}
