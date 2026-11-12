package com.lis.hr.service.impl;

import com.lis.common.core.constant.CacheKey;
import com.lis.common.redis.RedisUtil;
import com.lis.hr.entity.SysUserAccount;
import com.lis.hr.mapper.SysUserAccountMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * {@link AccountRevokeServiceImpl} 单元测试 —— B-4 (员工离职不回收账号) 修复验证。
 * <p>
 * 三条硬约束逐一断言:
 * <ol>
 *     <li><b>确实停用账号</b>: {@code sys_user.status} 被置 0;</li>
 *     <li><b>确实写 Redis 撤销标记</b>: 删 {@code USER_PERMISSIONS} + 写
 *         {@code USER_REVOKED} (TTL 24h);</li>
 *     <li><b>幂等 + 降级</b>: 已停用不重复写库; 任何异常都不得抛出, 以免回滚离职主流程。</li>
 * </ol>
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("B-4 AccountRevokeServiceImpl 离职账号回收")
class AccountRevokeServiceImplTest {

    private static final Long USER_ID = 1001L;

    @Mock
    private SysUserAccountMapper sysUserAccountMapper;

    @Mock
    private RedisUtil redisUtil;

    @InjectMocks
    private AccountRevokeServiceImpl service;

    private static SysUserAccount account(Long id, Integer status) {
        SysUserAccount a = new SysUserAccount();
        a.setId(id);
        a.setUsername("zhangsan");
        a.setStatus(status);
        return a;
    }

    // ==================== 正常路径 ====================

    @Test
    @DisplayName("启用中的账号: 停用写库 + 删权限缓存 + 写撤销标记, 三件事都必须发生")
    void revoke_enabledAccount_disablesAndMarks() {
        when(sysUserAccountMapper.selectById(USER_ID)).thenReturn(account(USER_ID, 1));

        service.revoke(USER_ID, "离职");

        // 1) 停用写库, 且只更新 id + status(0)
        ArgumentCaptor<SysUserAccount> captor = ArgumentCaptor.forClass(SysUserAccount.class);
        verify(sysUserAccountMapper, times(1)).updateById(captor.capture());
        SysUserAccount updated = captor.getValue();
        assertNotNull(updated);
        assertEquals(USER_ID, updated.getId(), "更新目标必须是该用户");
        assertEquals(0, updated.getStatus(), "status 必须被置 0 (停用)");

        // 2) 立即剥权
        verify(redisUtil, times(1)).delete(CacheKey.USER_PERMISSIONS + USER_ID);

        // 3) 写撤销标记
        verify(redisUtil, times(1)).set(eq(CacheKey.USER_REVOKED + USER_ID), any(),
                eq(24L), eq(TimeUnit.HOURS));
    }

    @Test
    @DisplayName("Redis key 前缀与 CacheKey 常量完全一致, 不得手写字符串")
    void revoke_redisKeysUseCacheKeyConstants() {
        when(sysUserAccountMapper.selectById(USER_ID)).thenReturn(account(USER_ID, 1));

        service.revoke(USER_ID, "离职");

        ArgumentCaptor<String> delKey = ArgumentCaptor.forClass(String.class);
        verify(redisUtil).delete(delKey.capture());
        assertEquals("lis:user:permissions:1001", delKey.getValue());

        ArgumentCaptor<String> setKey = ArgumentCaptor.forClass(String.class);
        verify(redisUtil).set(setKey.capture(), any(), anyLong(), any(TimeUnit.class));
        assertEquals("lis:user:revoked:1001", setKey.getValue());
    }

    @Test
    @DisplayName("撤销标记的值为时间戳, 供 Tier-2 网关按 JWT iat 比对")
    void revoke_markValueIsTimestamp() {
        when(sysUserAccountMapper.selectById(USER_ID)).thenReturn(account(USER_ID, 1));
        long before = System.currentTimeMillis();

        service.revoke(USER_ID, "离职");

        ArgumentCaptor<Object> value = ArgumentCaptor.forClass(Object.class);
        verify(redisUtil).set(anyString(), value.capture(), anyLong(), any(TimeUnit.class));
        Object v = value.getValue();
        assertEquals(Long.class, v.getClass(), "撤销标记应为毫秒时间戳");
        long ts = (Long) v;
        org.junit.jupiter.api.Assertions.assertTrue(ts >= before,
                "时间戳应不早于调用时刻, 实际: " + ts);
    }

    // ==================== 幂等 ====================

    @Test
    @DisplayName("幂等: 账号已 status=0 时跳过写库, 但仍刷新缓存与撤销标记")
    void revoke_alreadyDisabled_skipsDbWriteButStillMarks() {
        when(sysUserAccountMapper.selectById(USER_ID)).thenReturn(account(USER_ID, 0));

        service.revoke(USER_ID, "离职");

        verify(sysUserAccountMapper, never()).updateById(any());
        verify(redisUtil, times(1)).delete(CacheKey.USER_PERMISSIONS + USER_ID);
        verify(redisUtil, times(1)).set(eq(CacheKey.USER_REVOKED + USER_ID), any(),
                eq(24L), eq(TimeUnit.HOURS));
    }

    @Test
    @DisplayName("幂等: 连续撤销两次, 第二次 (已停用) 不再写库")
    void revoke_twice_secondSkipsDbWrite() {
        when(sysUserAccountMapper.selectById(USER_ID))
                .thenReturn(account(USER_ID, 1))
                .thenReturn(account(USER_ID, 0));

        service.revoke(USER_ID, "离职");
        service.revoke(USER_ID, "离职");

        verify(sysUserAccountMapper, times(1)).updateById(any());
        verify(redisUtil, times(2)).delete(anyString());
    }

    @Test
    @DisplayName("status 为 null (脏数据) 视为未停用, 执行写库")
    void revoke_nullStatus_treatedAsEnabled() {
        when(sysUserAccountMapper.selectById(USER_ID)).thenReturn(account(USER_ID, null));

        service.revoke(USER_ID, "离职");

        verify(sysUserAccountMapper, times(1)).updateById(any());
    }

    // ==================== 参数与不存在 ====================

    @Test
    @DisplayName("userId 非法 (null / 0 / 负数): 零交互, 不查库不写缓存")
    void revoke_illegalUserId_noInteraction() {
        service.revoke(null, "离职");
        service.revoke(0L, "离职");
        service.revoke(-1L, "离职");

        verifyNoInteractions(sysUserAccountMapper);
        verifyNoInteractions(redisUtil);
    }

    @Test
    @DisplayName("系统账号不存在: 查库后直接返回, 不写库也不写缓存, 不抛异常")
    void revoke_accountNotFound_returnsSilently() {
        when(sysUserAccountMapper.selectById(USER_ID)).thenReturn(null);

        assertDoesNotThrow(() -> service.revoke(USER_ID, "离职"));

        verify(sysUserAccountMapper, times(1)).selectById(USER_ID);
        verify(sysUserAccountMapper, never()).updateById(any());
        verifyNoInteractions(redisUtil);
    }

    // ==================== 降级 (最关键: 绝不阻断离职主流程) ====================

    @Test
    @DisplayName("降级: selectById 抛异常时不得向外抛出")
    void revoke_selectThrows_swallowed() {
        when(sysUserAccountMapper.selectById(USER_ID)).thenThrow(new RuntimeException("DB down"));
        assertDoesNotThrow(() -> service.revoke(USER_ID, "离职"),
                "账号服务异常绝不能冒泡, 否则会回滚离职登记");
    }

    @Test
    @DisplayName("降级: updateById 抛异常时不得向外抛出")
    void revoke_updateThrows_swallowed() {
        when(sysUserAccountMapper.selectById(USER_ID)).thenReturn(account(USER_ID, 1));
        when(sysUserAccountMapper.updateById(any())).thenThrow(new RuntimeException("update failed"));
        assertDoesNotThrow(() -> service.revoke(USER_ID, "离职"));
    }

    @Test
    @DisplayName("降级: Redis 不可用时不得向外抛出")
    void revoke_redisThrows_swallowed() {
        when(sysUserAccountMapper.selectById(USER_ID)).thenReturn(account(USER_ID, 1));
        doThrow(new RuntimeException("redis down")).when(redisUtil).delete(anyString());
        assertDoesNotThrow(() -> service.revoke(USER_ID, "离职"));
        // 写库已经发生, 只是缓存刷新失败
        verify(sysUserAccountMapper, times(1)).updateById(any());
    }
}
