import java.sql.*;
public class _CheckOcc8 {
  public static void main(String[] args) throws Exception {
    Class.forName("org.postgresql.Driver");
    try (Connection c = DriverManager.getConnection(
        "jdbc:postgresql://localhost:5432/lis_db?characterEncoding=utf8",
        "lis_user", "lis_dev_2026");
        Statement s = c.createStatement()) {
      System.out.println("--- occ=8 事件主信息 ---");
      ResultSet rs = s.executeQuery(
        "SELECT id, occ_no, project_code, project_name, group_name, trigger_rules, substr(qc_values,1,80) FROM lis_qc_occ_event WHERE id=8");
      while (rs.next()) {
        System.out.printf("  id=%d occ=%s code=%s name=[%s] group=[%s] rules=[%s] qc0=%s%n",
          rs.getLong(1), nvl(rs.getString(2),""), nvl(rs.getString(3),""),
          nvl(rs.getString(4),""), nvl(rs.getString(5),""), nvl(rs.getString(6),""), nvl(rs.getString(7),""));
      }
      System.out.println("\n--- occ=8 七步法步骤(checklist前200字) ---");
      rs = s.executeQuery(
        "SELECT id, step_no, name, coalesce(substr(checklist,1,200),'') FROM lis_qc_occ_step WHERE occ_id=8 ORDER BY step_no");
      while (rs.next()) {
        System.out.printf("  step.id=%d no=%d name=[%s] chk=[%s]%n",
          rs.getLong(1), rs.getInt(2), nvl(rs.getString(3),""), nvl(rs.getString(4),""));
      }
      System.out.println("\n--- occ=8 审批 ---");
      rs = s.executeQuery(
        "SELECT id, level_no, level_name, status FROM lis_qc_occ_approval WHERE occ_id=8 ORDER BY level_no");
      while (rs.next()) {
        System.out.printf("  apv.id=%d level=%d name=[%s] status=%s%n",
          rs.getLong(1), rs.getInt(2), nvl(rs.getString(3),""), nvl(rs.getString(4),""));
      }
    }
  }
  static String nvl(String s, String d) { return s==null?d:s; }
}
