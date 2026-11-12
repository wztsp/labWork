import java.sql.*;

public class TestReportQuery {
    public static void main(String[] args) throws Exception {
        String url = "jdbc:postgresql://localhost:5432/lis_db";
        String user = "lis_user";
        String password = "lis_dev_2026";

        Class.forName("org.postgresql.Driver");
        try (Connection conn = DriverManager.getConnection(url, user, password)) {
            String sql = "SELECT r.id, r.report_no, r.sample_id, r.patient_id, r.patient_name, " +
                    "r.status, r.auditor_id, r.audit_time, r.publisher_id, r.publish_time, " +
                    "r.report_doctor, r.checker, r.create_time, " +
                    "r.patient_gender, r.patient_age, " +
                    "NULL::INTEGER AS visit_type, " +
                    "''::VARCHAR AS department, " +
                    "''::VARCHAR AS doctor, ''::VARCHAR AS clinical_diag, " +
                    "''::VARCHAR AS sample_barcode, " +
                    "NULL::TIMESTAMP AS receive_time " +
                    "FROM lis_report r " +
                    "WHERE 1=1 AND r.status = 0 " +
                    "ORDER BY COALESCE(r.publish_time, r.create_time) DESC " +
                    "LIMIT 20 OFFSET 0";

            System.out.println("Executing SQL...");
            try (Statement s = conn.createStatement()) {
                ResultSet rs = s.executeQuery(sql);
                int count = 0;
                ResultSetMetaData meta = rs.getMetaData();
                int colCount = meta.getColumnCount();
                while (rs.next()) {
                    count++;
                    System.out.println("\nRow " + count + ":");
                    for (int i = 1; i <= colCount; i++) {
                        Object val = rs.getObject(i);
                        System.out.println("  " + meta.getColumnLabel(i) + " = " + val);
                    }
                }
                System.out.println("\nTotal rows: " + count);
            }
        }
    }
}
