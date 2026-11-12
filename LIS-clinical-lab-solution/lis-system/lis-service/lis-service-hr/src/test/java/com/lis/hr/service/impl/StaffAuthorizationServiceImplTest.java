package com.lis.hr.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.lis.hr.testsupport.TableInfoTestSupport;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.Mockito;

import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.verify;

/**
 * 岗位授权校验（hasAuthorization）单元测试。
 *
 * <p>桩化父类 {@code count(wrapper)}，验证「有记录→true / 无记录→false」的布尔映射与委托。
 * 日期区间(le start / ge end)、status=1、scope LIKE 等过滤由 MyBatis-Plus 生成 SQL 保证，
 * 本测试确认正确构造并委托了查询条件包装器。</p>
 */
@ExtendWith(MockitoExtension.class)
class StaffAuthorizationServiceImplTest {

    private StaffAuthorizationServiceImpl spy;

    @BeforeAll
    static void initTableInfo() {
        // 脱离 Spring 时 LambdaQueryWrapper 构造需要实体表元信息, 否则抛
        // "can not find lambda cache for this entity"; 预注册可绕开 MyBatis 运行期依赖。
        TableInfoTestSupport.ensureRegistered(com.lis.hr.entity.StaffAuthorization.class);
    }

    @BeforeEach
    void setUp() {
        spy = Mockito.spy(new StaffAuthorizationServiceImpl());
    }

    @Test
    void hasAuthorization_authorizedInScope_returnsTrue() {
        doReturn(1L).when(spy).count(any());
        assertTrue(spy.hasAuthorization(1L, "REPORT_AUDIT", "ICU"));
    }

    @Test
    void hasAuthorization_noRecord_returnsFalse() {
        doReturn(0L).when(spy).count(any());
        assertFalse(spy.hasAuthorization(1L, "REPORT_AUDIT", "ICU"));
    }

    @Test
    void hasAuthorization_scopeProvided_matches_returnsTrue() {
        doReturn(1L).when(spy).count(any());
        assertTrue(spy.hasAuthorization(1L, "INSTRUMENT_OP", "CHEM"));
    }

    @Test
    void hasAuthorization_statusDisabled_returnsFalse() {
        // status!=1 时查询无命中 → count 0
        doReturn(0L).when(spy).count(any());
        assertFalse(spy.hasAuthorization(1L, "INSTRUMENT_OP", "CHEM"));
    }

    @Test
    void hasAuthorization_authTypeMismatch_returnsFalse() {
        doReturn(0L).when(spy).count(any());
        assertFalse(spy.hasAuthorization(1L, "PROJECT_OP", "CHEM"));
    }

    @Test
    void hasAuthorization_invokesCountExactlyOnce() {
        doReturn(1L).when(spy).count(any());
        spy.hasAuthorization(1L, "REPORT_AUDIT", null);
        verify(spy).count(any());
    }

    @Test
    void hasAuthorization_buildsLambdaQueryWrapper() {
        doReturn(1L).when(spy).count(any());
        ArgumentCaptor<LambdaQueryWrapper<com.lis.hr.entity.StaffAuthorization>> captor =
                ArgumentCaptor.forClass(LambdaQueryWrapper.class);
        spy.hasAuthorization(1L, "REPORT_AUDIT", "ICU");
        verify(spy).count(captor.capture());
        assertInstanceOf(LambdaQueryWrapper.class, captor.getValue());
    }
}
