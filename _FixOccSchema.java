import java.sql.*;
public class _FixOccSchema {
  public static void main(String[] args) throws Exception {
    Class.forName("org.postgresql.Driver");
    try (Connection c = DriverManager.getConnection(
        "jdbc:postgresql://localhost:5432/lis_db?characterEncoding=utf8",
        "lis_user", "lis_dev_2026");
        Statement s = c.createStatement()) {

      // --- 1. 扩字段：qc_values/trigger_rules 从 VARCHAR(200) 扩到 TEXT，避免右截断(200) ---
      try { s.execute("ALTER TABLE lis_qc_occ_event ALTER COLUMN qc_values TYPE TEXT");
            System.out.println("[OK] lis_qc_occ_event.qc_values -> TEXT"); }
      catch (Exception e) { System.out.println("[SKIP] qc_values: " + e.getMessage()); }

      try { s.execute("ALTER TABLE lis_qc_occ_event ALTER COLUMN trigger_rules TYPE TEXT");
            System.out.println("[OK] lis_qc_occ_event.trigger_rules -> TEXT"); }
      catch (Exception e) { System.out.println("[SKIP] trigger_rules: " + e.getMessage()); }

      // --- 2. 打步骤表：看乱码是DB存的就是乱码，还是前端渲染问题 ---
      System.out.println("\n--- lis_qc_occ_step 前 49 行(checklist前180字) ---");
      ResultSet rs = s.executeQuery(
        "SELECT id, occ_id, step_no, name, coalesce(substr(checklist,1,180),'') chk FROM lis_qc_occ_step ORDER BY occ_id, step_no LIMIT 49");
      while (rs.next()) {
        System.out.printf("  id=%-3d occ=%-3d step=%d name=[%-30s] chk=[%s]%n",
          rs.getLong(1), rs.getLong(2), rs.getInt(3),
          nvl(rs.getString(4),""), nvl(rs.getString(5),""));
      }

      // --- 3. 打事件表：看项目名/触发规则 ---
      System.out.println("\n--- lis_qc_occ_event 全部 ---");
      rs = s.executeQuery(
        "SELECT id, occ_no, status, current_step, project_code, project_name, trigger_rules, substr(qc_values,1,80) FROM lis_qc_occ_event WHERE deleted=0 ORDER BY id");
      while (rs.next()) {
        System.out.printf("  id=%-3d occ=%-24s status=%-14s step=%d code=%-8s name=[%-20s] rules=[%s] qc0=%s%n",
          rs.getLong(1), nvl(rs.getString(2),""), nvl(rs.getString(3),""),
          rs.getInt(4), nvl(rs.getString(5),""), nvl(rs.getString(6),""),
          nvl(rs.getString(7),""), nvl(rs.getString(8),""));
      }
    }
  }
  static String nvl(String s, String d) { return s==null?d:s; }
}
