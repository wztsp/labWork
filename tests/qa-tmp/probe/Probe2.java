import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import java.io.Serializable;

public class Probe2 {
    @TableName("lis_demo")
    public static class Demo implements Serializable {
        @TableId(type = IdType.AUTO)
        private Long id;
        private String userName;
        public Long getId() { return id; }
        public void setId(Long id) { this.id = id; }
        public String getUserName() { return userName; }
        public void setUserName(String u) { this.userName = u; }
    }

    public static void main(String[] args) {
        try {
            MybatisConfiguration cfg = new MybatisConfiguration();
            MapperBuilderAssistant assistant = new MapperBuilderAssistant(cfg, "");
            assistant.setCurrentNamespace("probe.DemoMapper");
            TableInfoHelper.initTableInfo(assistant, Demo.class);

            LambdaQueryWrapper<Demo> w = new LambdaQueryWrapper<>();
            w.eq(Demo::getUserName, "abc").ge(Demo::getId, 5L);
            System.out.println("OK sqlSegment = " + w.getSqlSegment());
            System.out.println("OK params     = " + w.getParamNameValuePairs());
        } catch (Throwable t) {
            t.printStackTrace();
        }
    }
}
