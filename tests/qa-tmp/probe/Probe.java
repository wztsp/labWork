import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import java.io.Serializable;

public class Probe {
    @TableName("lis_demo")
    public static class Demo implements Serializable {
        private Long id;
        private String name;
        public Long getId() { return id; }
        public void setId(Long id) { this.id = id; }
        public String getName() { return name; }
        public void setName(String name) { this.name = name; }
    }

    public static void main(String[] args) {
        try {
            LambdaQueryWrapper<Demo> w = new LambdaQueryWrapper<>();
            w.eq(Demo::getName, "abc");
            System.out.println("OK sqlSegment = " + w.getSqlSegment());
            System.out.println("OK params = " + w.getParamNameValuePairs());
        } catch (Throwable t) {
            System.out.println("THROWN: " + t.getClass().getName() + " : " + t.getMessage());
        }
    }
}
