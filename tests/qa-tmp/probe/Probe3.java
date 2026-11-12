import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.lis.sample.entity.Sample;
import com.lis.sample.entity.BarcodeRule;
import com.lis.reagent.entity.ReagentStock;
import com.lis.hr.entity.StaffAuthorization;
import com.lis.hr.entity.StaffCertificate;
import org.apache.ibatis.builder.MapperBuilderAssistant;

import java.time.LocalDate;

public class Probe3 {

    static void ensure(Class<?> c) {
        if (TableInfoHelper.getTableInfo(c) == null) {
            MapperBuilderAssistant a = new MapperBuilderAssistant(new MybatisConfiguration(), "");
            a.setCurrentNamespace("test." + c.getSimpleName() + "Mapper");
            TableInfoHelper.initTableInfo(a, c);
        }
    }

    public static void main(String[] args) {
        try {
            ensure(Sample.class);
            ensure(BarcodeRule.class);
            ensure(ReagentStock.class);
            ensure(StaffAuthorization.class);
            ensure(StaffCertificate.class);

            LambdaQueryWrapper<Sample> w1 = new LambdaQueryWrapper<>();
            w1.eq(Sample::getSampleBarcode, "S001");
            System.out.println("[Sample query] " + w1.getSqlSegment() + " | " + w1.getParamNameValuePairs());

            LambdaUpdateWrapper<Sample> w2 = new LambdaUpdateWrapper<>();
            w2.eq(Sample::getId, 1L).set(Sample::getStatus, 1).set(Sample::getDeliverTime, null);
            System.out.println("[Sample update] set=" + w2.getSqlSet() + " where=" + w2.getSqlSegment());

            LambdaQueryWrapper<StaffAuthorization> w3 = new LambdaQueryWrapper<StaffAuthorization>()
                    .eq(StaffAuthorization::getStaffId, 1L)
                    .eq(StaffAuthorization::getAuthType, "REPORT_AUDIT")
                    .eq(StaffAuthorization::getStatus, 1)
                    .le(StaffAuthorization::getStartDate, LocalDate.now())
                    .ge(StaffAuthorization::getEndDate, LocalDate.now())
                    .like(StaffAuthorization::getAuthScope, "SHENGHUA");
            System.out.println("[Auth] " + w3.getSqlSegment());
            System.out.println("[Auth params] " + w3.getParamNameValuePairs());

            LambdaQueryWrapper<ReagentStock> w4 = new LambdaQueryWrapper<ReagentStock>()
                    .eq(ReagentStock::getReagentId, 9L)
                    .eq(ReagentStock::getStatus, 1)
                    .gt(ReagentStock::getExpiryDate, LocalDate.now())
                    .orderByAsc(ReagentStock::getExpiryDate);
            System.out.println("[Stock] " + w4.getSqlSegment());

            LambdaQueryWrapper<StaffCertificate> w5 = new LambdaQueryWrapper<StaffCertificate>()
                    .eq(StaffCertificate::getStatus, 1)
                    .between(StaffCertificate::getExpiryDate, LocalDate.now(), LocalDate.now().plusDays(30));
            System.out.println("[Cert] " + w5.getSqlSegment() + " | " + w5.getParamNameValuePairs());

            LambdaQueryWrapper<BarcodeRule> w6 = new LambdaQueryWrapper<BarcodeRule>()
                    .eq(BarcodeRule::getRuleCode, "SAMPLE")
                    .eq(BarcodeRule::getEnabled, 1)
                    .last("LIMIT 1");
            System.out.println("[Rule] " + w6.getSqlSegment());

            System.out.println("ALL-OK");
        } catch (Throwable t) {
            t.printStackTrace();
        }
    }
}
