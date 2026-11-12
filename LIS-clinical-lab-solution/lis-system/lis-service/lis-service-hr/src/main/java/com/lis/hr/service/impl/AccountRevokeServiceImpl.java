package com.lis.hr.service.impl;

import com.lis.common.core.constant.CacheKey;
import com.lis.common.redis.RedisUtil;
import com.lis.hr.entity.SysUserAccount;
import com.lis.hr.mapper.SysUserAccountMapper;
import com.lis.hr.service.AccountRevokeService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.concurrent.TimeUnit;

/**
 * 系统账号撤销服务实现 (B-4 离职回收)。
 * <p>
 * <b>Tier-1 范围 (本轮实现)</b>:
 * <ol>
 *     <li>将 {@code sys_user.status} 置 0 (停用), 已停用则跳过, 幂等;</li>
 *     <li>立即删除权限缓存 {@code USER_PERMISSIONS:{uid}} 实现即时剥权;</li>
 *     <li>写入撤销标记 {@code USER_REVOKED:{uid}}, TTL = 24h (与 JWT 有效期对齐)。</li>
 * </ol>
 * <p>
 * <b>Tier-2 (按 iat 拒绝旧 token)</b>: 已实现。认证过滤器
 * {@code TokenAuthenticationFilter#isRevoked} 读取 {@code USER_REVOKED:{uid}}
 * 并按 JWT 签发时间 (iat) 拒绝撤销前签发的旧 token, 实现离职即刻踢下线。
 * <p>
 * <b>降级约定</b>: 全程 try-catch, 任何异常仅 warn, <b>绝不抛出</b>,
 * 保证离职主流程不被账号服务异常回滚。
 */
@Slf4j
@Service
public class AccountRevokeServiceImpl implements AccountRevokeService {

    /** 撤销标记 TTL, 与 JWT 有效期对齐, 单位: 小时 */
    private static final long REVOKE_MARK_TTL_HOURS = 24L;

    private final SysUserAccountMapper sysUserAccountMapper;
    private final RedisUtil redisUtil;

    public AccountRevokeServiceImpl(SysUserAccountMapper sysUserAccountMapper, RedisUtil redisUtil) {
        this.sysUserAccountMapper = sysUserAccountMapper;
        this.redisUtil = redisUtil;
    }

    @Override
    public void revoke(Long userId, String reason) {
        try {
            if (userId == null || userId <= 0L) {
                log.warn("[账号撤销] userId 非法, 跳过。userId={}, reason={}", userId, reason);
                return;
            }
            SysUserAccount account = sysUserAccountMapper.selectById(userId);
            if (account == null) {
                log.warn("[账号撤销] 系统账号不存在, 跳过。userId={}, reason={}", userId, reason);
                return;
            }
            // 幂等: 已停用 (status=0) 则不重复写库
            if (!Integer.valueOf(0).equals(account.getStatus())) {
                SysUserAccount update = new SysUserAccount();
                update.setId(userId);
                update.setStatus(0);
                sysUserAccountMapper.updateById(update);
                log.info("[账号撤销] 系统账号已停用。userId={}, reason={}", userId, reason);
            } else {
                log.info("[账号撤销] 系统账号已处于停用状态, 跳过写库。userId={}, reason={}", userId, reason);
            }
            // 立即剥权: 删除权限缓存, 下次鉴权将重新加载 (此时 status=0 会被拦)
            redisUtil.delete(CacheKey.USER_PERMISSIONS + userId);
            // 写入撤销标记, 供审计或 Tier-2 网关 (按 iat) 识别
            redisUtil.set(CacheKey.USER_REVOKED + userId, System.currentTimeMillis(),
                    REVOKE_MARK_TTL_HOURS, TimeUnit.HOURS);
        } catch (Exception e) {
            // 降级: 账号撤销失败不得阻断主流程 (如离职登记)
            log.warn("[账号撤销] 撤销系统账号时发生异常, 已降级处理。userId=" + userId + ", reason=" + reason, e);
        }
    }
}
