package com.lis.gateway.filter;

import com.lis.common.security.JwtUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.util.AntPathMatcher;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;

/**
 * JWT 鉴权全局过滤器
 * <p>
 * 从请求头提取 Bearer token, 校验 JWT 有效性,
 * 将 userId/username 注入请求头传递给下游服务。
 * 白名单路径直接放行。
 */
@Component
public class AuthFilter implements GlobalFilter, Ordered {

    private static final Logger log = LoggerFactory.getLogger(AuthFilter.class);

    private static final String AUTH_HEADER = "Authorization";
    private static final String BEARER_PREFIX = "Bearer ";

    /** 下游服务读取的用户信息请求头 */
    private static final String HEADER_USER_ID = "X-User-Id";
    private static final String HEADER_USERNAME = "X-Username";

    /** 白名单路径 */
    private static final List<String> WHITELIST = Arrays.asList(
            "/api/auth/**",
            "/doc.html",
            "/swagger-ui/**",
            "/swagger-ui.html",
            "/webjars/**",
            "/v3/api-docs/**",
            // ===== 通信程序 / 仪器结果回传：允许外部通信程序在无登录态下推送结果 =====
            "/api/report/micro/import",
            "/api/report/micro/instrument-results/**",
            "/api/instrument/data/**",
            "/api/instrument/*/send-micro",
            "/api/result/instrument/**"
    );

    private final AntPathMatcher pathMatcher = new AntPathMatcher();

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        ServerHttpRequest request = exchange.getRequest();
        String path = request.getURI().getPath();

        // 白名单放行
        if (isWhitelisted(path)) {
            return chain.filter(exchange);
        }

        // 提取 token
        HttpHeaders headers = request.getHeaders();
        String authHeader = headers.getFirst(AUTH_HEADER);
        if (authHeader == null || !authHeader.startsWith(BEARER_PREFIX)) {
            return unauthorized(exchange, "缺少认证令牌");
        }

        String token = authHeader.substring(BEARER_PREFIX.length()).trim();
        // 校验 JWT
        if (!JwtUtil.validateToken(token)) {
            return unauthorized(exchange, "认证令牌无效或已过期");
        }

        Long userId;
        String username;
        try {
            userId = JwtUtil.getUserId(token);
            username = JwtUtil.getUsername(token);
        } catch (Exception e) {
            log.warn("解析 JWT 失败: {}", e.getMessage());
            return unauthorized(exchange, "认证令牌解析失败");
        }

        // 将用户信息注入请求头传递给下游
        ServerHttpRequest mutated = request.mutate()
                .header(HEADER_USER_ID, String.valueOf(userId))
                .header(HEADER_USERNAME, username == null ? "" : username)
                .build();

        return chain.filter(exchange.mutate().request(mutated).build());
    }

    @Override
    public int getOrder() {
        // 优先级高于 LogFilter, 鉴权失败时直接返回
        return -100;
    }

    private boolean isWhitelisted(String path) {
        for (String pattern : WHITELIST) {
            if (pathMatcher.match(pattern, path)) {
                return true;
            }
        }
        return false;
    }

    private Mono<Void> unauthorized(ServerWebExchange exchange, String message) {
        ServerHttpResponse response = exchange.getResponse();
        response.setStatusCode(HttpStatus.UNAUTHORIZED);
        response.getHeaders().setContentType(MediaType.APPLICATION_JSON);
        String body = "{\"code\":401,\"message\":\"" + message + "\",\"data\":null}";
        DataBuffer buffer = response.bufferFactory().wrap(body.getBytes(StandardCharsets.UTF_8));
        return response.writeWith(Mono.just(buffer));
    }
}
