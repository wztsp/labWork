package com.lis.common.security;

import com.lis.common.core.constant.CacheKey;
import com.lis.common.redis.RedisUtil;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * 权限校验拦截器。
 * <p>
 * 对标注了 {@link RequirePermission} 的 Controller 方法/类强制执行权限校验:
 * <ol>
 *   <li>优先从网关注入的 {@code X-User-Id} 请求头取得当前用户;</li>
 *   <li>若网关头缺失, 从 {@code Authorization: Bearer <token>} JWT 令牌解析用户ID (直连降级);</li>
 *   <li>从 Redis 加载该用户的权限码列表(登录时由 AuthService 缓存);</li>
 *   <li>若缺少所需权限, 返回 401(未登录) / 403(无权限)。</li>
 * </ol>
 * 未标注注解的接口直接放行, 不影响现有业务。
 */
@Component
public class PermissionInterceptor implements HandlerInterceptor {

    private static final Logger log = LoggerFactory.getLogger(PermissionInterceptor.class);

    private static final String HEADER_USER_ID = "X-User-Id";
    private static final String AUTH_HEADER = "Authorization";
    private static final String BEARER_PREFIX = "Bearer ";

    private final RedisUtil redisUtil;

    public PermissionInterceptor(RedisUtil redisUtil) {
        this.redisUtil = redisUtil;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler)
            throws Exception {
        // 仅对 Controller 方法生效
        if (!(handler instanceof HandlerMethod handlerMethod)) {
            return true;
        }

        // 从方法或类上取注解(方法优先)
        RequirePermission require =
                handlerMethod.getMethodAnnotation(RequirePermission.class);
        if (require == null) {
            require = handlerMethod.getBeanType().getAnnotation(RequirePermission.class);
        }
        if (require == null) {
            return true; // 未配置权限的接口直接放行
        }

        String required = require.value();
        String userIdStr = resolveUserId(request);
        if (userIdStr == null || userIdStr.isEmpty()) {
            writeJson(response, HttpServletResponse.SC_UNAUTHORIZED, "未登录或缺少用户身份");
            return false;
        }

        // 超级管理员 (id=1) 豁免所有权限校验
        if ("1".equals(userIdStr)) {
            return true;
        }

        // 检查 Redis 是否可用 (通过尝试 ping)
        boolean redisAvailable = isRedisAvailable();

        List<String> permissions;
        try {
            Object cached = redisUtil.get(CacheKey.USER_PERMISSIONS + userIdStr);
            if (cached instanceof List<?> list) {
                @SuppressWarnings("unchecked")
                List<String> perms = (List<String>) list;
                permissions = perms;
            } else {
                permissions = List.of();
            }
        } catch (Exception e) {
            // 权限加载失败按无权限处理, 安全起见 fail-closed
            log.error("加载用户权限失败, userId={}", userIdStr, e);
            writeJson(response, HttpServletResponse.SC_FORBIDDEN, "权限校验失败, 请稍后重试");
            return false;
        }

        // Redis 不可用时降级: 只要用户已通过 JWT 认证就放行 (开发环境友好)
        if (!redisAvailable && permissions.isEmpty()) {
            log.warn("Redis 不可用, 降级放行用户 {} 对 {} 的访问", userIdStr, request.getRequestURI());
            return true;
        }

        if (permissions.contains(required)) {
            return true;
        }

        // Redis 不可用且无缓存时, 降级放行 (避免 Redis 故障导致全部 403)
        if (!redisAvailable) {
            log.warn("Redis 不可用且无缓存权限, 降级放行用户 {} 对 {} 的访问", userIdStr, request.getRequestURI());
            return true;
        }

        log.warn("用户 {} 无权限访问 {}: 缺少权限 {}", userIdStr, request.getRequestURI(), required);
        writeJson(response, HttpServletResponse.SC_FORBIDDEN, "无权限执行该操作");
        return false;
    }

    /**
     * 解析用户ID: 优先使用网关注入的 X-User-Id 头, 缺失时从 JWT 令牌解析。
     */
    private String resolveUserId(HttpServletRequest request) {
        // 1. 优先使用网关注入的请求头
        String userIdStr = request.getHeader(HEADER_USER_ID);
        if (userIdStr != null && !userIdStr.isEmpty()) {
            return userIdStr;
        }

        // 2. 降级: 从 Authorization 头解析 JWT
        String authHeader = request.getHeader(AUTH_HEADER);
        if (authHeader != null && authHeader.startsWith(BEARER_PREFIX)) {
            String token = authHeader.substring(BEARER_PREFIX.length()).trim();
            try {
                if (JwtUtil.validateToken(token)) {
                    Long userId = JwtUtil.getUserId(token);
                    if (userId != null) {
                        return String.valueOf(userId);
                    }
                } else {
                    log.warn("JWT 令牌无效或已过期, URI={}", request.getRequestURI());
                }
            } catch (Exception e) {
                log.warn("解析 JWT 令牌失败, URI={}, error={}", request.getRequestURI(), e.getMessage());
            }
        }

        return null;
    }

    /**
     * 检测 Redis 是否可用 (通过尝试执行一个简单操作)。
     */
    private boolean isRedisAvailable() {
        try {
            redisUtil.get("__ping__");
            return true;
        } catch (Exception e) {
            log.debug("Redis 不可用: {}", e.getMessage());
            return false;
        }
    }

    private void writeJson(HttpServletResponse response, int status, String message) throws Exception {
        response.setStatus(status);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        String body = "{\"code\":" + status + ",\"message\":\"" + message + "\",\"data\":null}";
        response.getWriter().write(body);
    }
}