package com.lis.hr.service.impl;

import com.lis.hr.entity.StaffCertificate;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.lis.hr.testsupport.TableInfoTestSupport;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;

/**
 * 资质证书到期扫描（listExpiring）单元测试。
 *
 * <p>桩化父类 {@code list(wrapper)}，模拟数据库已按 status=1 且
 * expiry_date 落在 [today, today+withinDays] 内返回的结果，验证透传与数量正确。</p>
 */
@ExtendWith(MockitoExtension.class)
class StaffCertificateServiceImplTest {

    private StaffCertificateServiceImpl spy;

    @BeforeAll
    static void initTableInfo() {
        // 脱离 Spring 时 LambdaQueryWrapper 构造需要实体表元信息, 否则抛
        // "can not find lambda cache for this entity"; 预注册可绕开 MyBatis 运行期依赖。
        TableInfoTestSupport.ensureRegistered(StaffCertificate.class);
    }

    @BeforeEach
    void setUp() {
        spy = Mockito.spy(new StaffCertificateServiceImpl());
    }

    private static StaffCertificate cert(Long id, LocalDate expiry, int status) {
        StaffCertificate c = new StaffCertificate();
        c.setId(id);
        c.setStaffId(1L);
        c.setExpiryDate(expiry);
        c.setStatus(status);
        return c;
    }

    @Test
    void listExpiring_expiryToday_included() {
        doReturn(List.of(cert(1L, LocalDate.now(), 1))).when(spy).list(any(Wrapper.class));
        assertEquals(1, spy.listExpiring(10).size());
    }

    @Test
    void listExpiring_expiryWithinWindow_included() {
        doReturn(List.of(cert(1L, LocalDate.now().plusDays(5), 1))).when(spy).list(any(Wrapper.class));
        assertEquals(1, spy.listExpiring(10).size());
    }

    @Test
    void listExpiring_beyondWindow_returnsEmpty() {
        doReturn(List.of()).when(spy).list(any(Wrapper.class));
        assertEquals(0, spy.listExpiring(10).size());
    }

    @Test
    void listExpiring_multipleCerts_returnsAllFromQuery() {
        doReturn(List.of(
                cert(1L, LocalDate.now().plusDays(2), 1),
                cert(2L, LocalDate.now().plusDays(8), 1))).when(spy).list(any(Wrapper.class));
        assertEquals(2, spy.listExpiring(10).size());
    }

    @Test
    void listExpiring_zeroWithinDays_returnsExpiringTodayOnly() {
        // withinDays=0 → 截止日 = 今天，仅今天到期会被查询返回
        doReturn(List.of(cert(1L, LocalDate.now(), 1))).when(spy).list(any(Wrapper.class));
        assertEquals(1, spy.listExpiring(0).size());
    }
}
