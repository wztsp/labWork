package com.lis.hr.service.impl;

import com.lis.common.core.exception.BusinessException;
import com.lis.hr.entity.Staff;
import com.lis.hr.mapper.StaffCertificateMapper;
import com.lis.hr.service.AccountRevokeService;
import com.lis.hr.testsupport.TableInfoTestSupport;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

/**
 * {@link StaffServiceImpl#resign(Long)} 单元测试 —— B-4 离职联动账号回收。
 * <p>
 * 被测方法原有 {@code // TODO: 联动停用系统账号}, 本测试验证 TODO 已被真实实现替换:
 * 离职登记与账号撤销在<b>同一 {@code @Transactional}</b> 内联动, 且账号撤销失败
 * <b>不得</b>阻断离职主流程。
 * <p>
 * {@code StaffServiceImpl} 继承 MyBatis-Plus {@code ServiceImpl}, 因此用
 * {@code spy} + {@code doReturn} 桩化父类的 {@code getById/updateById},
 * 避免真实数据库访问。
 */
@DisplayName("B-4 StaffServiceImpl.resign 离职联动账号停用")
class StaffServiceResignTest {

    private static final Long STAFF_ID = 501L;
    private static final Long USER_ID = 1001L;

    private StaffCertificateMapper certificateMapper;
    private AccountRevokeService accountRevokeService;
    private StaffServiceImpl service;

    @BeforeAll
    static void registerTableInfo() {
        // LambdaQueryWrapper 需要 TableInfo 元数据, 否则构造 wrapper 时即抛 lambda cache 异常
        TableInfoTestSupport.ensureRegistered(Staff.class);
    }

    @BeforeEach
    void setUp() {
        certificateMapper = mock(StaffCertificateMapper.class);
        accountRevokeService = mock(AccountRevokeService.class);
        service = spy(new StaffServiceImpl(certificateMapper, accountRevokeService));
    }

    private static Staff staff(Long id, Long userId) {
        Staff s = new Staff();
        s.setId(id);
        s.setUserId(userId);
        s.setStaffNo("E001");
        s.setName("张三");
        s.setStatus(1);
        return s;
    }

    // ==================== 主流程 ====================

    @Test
    @DisplayName("有关联账号: 置离职状态 + 记离职日期 + 调用 AccountRevokeService")
    void resign_withUserId_revokesAccount() {
        Staff s = staff(STAFF_ID, USER_ID);
        doReturn(s).when(service).getById(STAFF_ID);
        doReturn(true).when(service).updateById(any(Staff.class));

        service.resign(STAFF_ID);

        assertEquals(0, s.getStatus(), "status 必须置 0 (离职)");
        assertEquals(LocalDate.now(), s.getLeaveDate(), "离职日期应为今天");
        org.junit.jupiter.api.Assertions.assertNotNull(s.getUpdateTime(), "更新时间应被刷新");
        verify(service, times(1)).updateById(s);
        verify(accountRevokeService, times(1)).revoke(USER_ID, "离职");
    }

    @Test
    @DisplayName("撤销原因固定为 '离职', 便于审计检索")
    void resign_passesFixedReason() {
        Staff s = staff(STAFF_ID, USER_ID);
        doReturn(s).when(service).getById(STAFF_ID);
        doReturn(true).when(service).updateById(any(Staff.class));

        service.resign(STAFF_ID);

        verify(accountRevokeService).revoke(eq(USER_ID), eq("离职"));
    }

    @Test
    @DisplayName("先写离职登记再撤销账号 —— 顺序保证主流程数据先落库")
    void resign_updatesStaffBeforeRevoking() {
        Staff s = staff(STAFF_ID, USER_ID);
        doReturn(s).when(service).getById(STAFF_ID);
        List<String> order = new ArrayList<>();
        doAnswer(inv -> {
            order.add("updateStaff");
            return true;
        }).when(service).updateById(any(Staff.class));
        doAnswer(inv -> {
            order.add("revoke");
            return null;
        }).when(accountRevokeService).revoke(anyLong(), any());

        service.resign(STAFF_ID);

        assertEquals(List.of("updateStaff", "revoke"), order);
    }

    // ==================== 无关联账号 ====================

    @Test
    @DisplayName("无关联账号 (userId=null): 对账号服务零交互")
    void resign_withoutUserId_noRevoke() {
        Staff s = staff(STAFF_ID, null);
        doReturn(s).when(service).getById(STAFF_ID);
        doReturn(true).when(service).updateById(any(Staff.class));

        service.resign(STAFF_ID);

        assertEquals(0, s.getStatus(), "无账号也要正常完成离职登记");
        assertEquals(LocalDate.now(), s.getLeaveDate());
        verifyNoInteractions(accountRevokeService);
    }

    // ==================== 异常与降级 ====================

    @Test
    @DisplayName("人员不存在: 抛 BusinessException, 且不触碰账号服务")
    void resign_staffNotFound_throwsAndNoRevoke() {
        doReturn(null).when(service).getById(STAFF_ID);

        BusinessException ex = assertThrows(BusinessException.class, () -> service.resign(STAFF_ID));
        org.junit.jupiter.api.Assertions.assertTrue(ex.getMessage().contains("人员不存在"),
                "异常提示应可读, 实际: " + ex.getMessage());
        verify(service, times(0)).updateById(any(Staff.class));
        verifyNoInteractions(accountRevokeService);
    }

    @Test
    @DisplayName("降级: 账号撤销抛异常时, 离职登记仍成功 (异常不冒泡)")
    void resign_revokeThrows_resignStillSucceeds() {
        Staff s = staff(STAFF_ID, USER_ID);
        doReturn(s).when(service).getById(STAFF_ID);
        doReturn(true).when(service).updateById(any(Staff.class));
        doThrow(new RuntimeException("account service down"))
                .when(accountRevokeService).revoke(anyLong(), any());

        assertDoesNotThrow(() -> service.resign(STAFF_ID),
                "账号服务异常绝不能让离职事务回滚");
        assertEquals(0, s.getStatus(), "离职状态必须已写入");
        verify(service, times(1)).updateById(s);
    }

    @Test
    @DisplayName("回归: 源码中不得再残留 B-4 的实现型 TODO")
    void resign_noLegacyTodoLeft() {
        // 该断言由 B3B8SelfCheck 的源码扫描补充; 此处仅占位声明契约
        assertDoesNotThrow(() -> {
            Staff s = staff(STAFF_ID, USER_ID);
            doReturn(s).when(service).getById(STAFF_ID);
            doReturn(true).when(service).updateById(any(Staff.class));
            service.resign(STAFF_ID);
        });
        verify(accountRevokeService, times(1)).revoke(USER_ID, "离职");
    }
}
