package com.lis.auth.filter;

import com.lis.common.core.constant.CacheKey;
import com.lis.common.redis.RedisUtil;
import com.lis.common.security.JwtUtil;
import com.lis.common.security.SecurityContext;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Date;
import java.util.List;

/**
     * 认证过滤器
     * <p>
     * /api/auth/** 路径在网关白名单中, 网关不会注入用户信息头,
     * 因此认证服务自行解析 Authorization 中的 JWT 并填充 SecurityContext。
     */
@Component
public class TokenAuthenticationFilter extends OncePerRequestFilter {

    private static final String AUTH_HEADER = "Authorization";
    private static final String BEARER_PREFIX = "Bearer ";

    private final RedisUtil redisUtil;

    public TokenAuthenticationFilter(RedisUtil redisUtil) {
        this.redisUtil = redisUtil;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String authHeader = request.getHeader(AUTH_HEADER);
        if (authHeader != null && authHeader.startsWith(BEARER_PREFIX)) {
            String token = authHeader.substring(BEARER_PREFIX.length()).trim();
            try {
                if (JwtUtil.validateToken(token)
                        && !Boolean.TRUE.equals(redisUtil.hasKey(CacheKey.TOKEN_BLACKLIST + token))) {
                    Long userId = JwtUtil.getUserId(token);
                    // B-4 Tier-2: 账号已撤销(如离职)时, 拒绝签发时间早于撤销标记的旧 token
                    if (!isRevoked(userId, token)) {
                        String username = JwtUtil.getUsername(token);

                        SecurityContext.LoginUser loginUser = new SecurityContext.LoginUser(userId, username, null);
                        // 从 Redis 加载权限
                        Object cached = redisUtil.get(CacheKey.USER_PERMISSIONS + userId);
                        if (cached instanceof List<?>) {
                            @SuppressWarnings("unchecked")
                            List<String> perms = (List<String>) cached;
                            loginUser.setPermissions(perms);
                        }
                        SecurityContext.set(loginUser);
                    }
                }
            } catch (Exception ignored) {
                // token 无效, 不填充上下文, 由下游业务判断
            }
        }

        try {
            filterChain.doFilter(request, response);
        } finally {
            SecurityContext.clear();
        }
    }

    /**
     * B-4 Tier-2: 判断 token 是否因账号撤销(如离职)而失效。
     * 撤销标记 {@link CacheKey#USER_REVOKED} 存的是撤销时刻的毫秒时间戳,
     * 若 token 的签发时间 (iat) 早于撤销时刻, 判定为"撤销前签发的旧 token"→ 拒绝。
     * 任何异常均按未撤销处理, 不阻断正常鉴权。
     */
    private boolean isRevoked(Long userId, String token) {
        try {
            Object mark = redisUtil.get(CacheKey.USER_REVOKED + userId);
            if (mark == null) {
                return false;
            }
            long revokedAt = Long.parseLong(String.valueOf(mark));
            Date issuedAt = JwtUtil.getIssuedAt(token);
            return issuedAt != null && issuedAt.getTime() < revokedAt;
        } catch (Exception e) {
            return false;
        }
    }
}
