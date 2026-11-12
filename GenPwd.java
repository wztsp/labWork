import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
public class GenPwd {
  public static void main(String[] args) throws Exception {
    BCryptPasswordEncoder e = new BCryptPasswordEncoder();
    String hash = "$2a$10$N.ZOn9G6/YLFixAOPMg/h.z7pCu6v2XyFDtC4q.jeeGm/TEZyj3C6";
    for (String pwd : new String[]{"123456", "admin123", "admin", "lis_dev_2026", "Lis@123"}) {
      boolean ok = e.matches(pwd, hash);
      System.out.println(pwd + " => " + ok);
    }
    System.out.println("NEW hash for 123456 => " + e.encode("123456"));
  }
}
