package com.lis.monolith.config;

import jakarta.servlet.*;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;

import java.io.IOException;

/**
 * SPA 路由回退：在请求进入 Spring MVC 之前，对 SPA 路径直接 forward 到 /index.html。
 * 这样 Vue Router 的 history 模式（如 /report/technician）刷新时不会 404。
 * <p>
 * 跳过的路径（正常走 Spring MVC / 资源 handler）:
 *   /        → Spring Boot 默认 index.html
 *   /api/**  → 后端 REST
 *   /actuator/** → 监控
 *   /ws/**   → WebSocket
 *   /assets/** → 前端打包的 JS/CSS
 *   带扩展名的 (.js .css .png ...)
 *   favicon.ico, sw.js 等
 */
@Configuration
public class SpaFallbackConfig {

    private static final String[] SKIP_PREFIXES = {
            "/api", "/actuator", "/ws", "/assets",
            "/favicon", "/sw.js", "/robots.txt"
    };

    @Bean
    public FilterRegistrationBean<Filter> spaFallbackFilter() {
        FilterRegistrationBean<Filter> registration = new FilterRegistrationBean<>();
        registration.setFilter(new RequestForwardFilter());
        registration.addUrlPatterns("/*");
        registration.setName("spaFallbackFilter");
        // 在 Spring MVC DispatcherServlet 之前执行，直接 forward
        registration.setOrder(Ordered.HIGHEST_PRECEDENCE);
        return registration;
    }

    static class RequestForwardFilter implements Filter {

        @Override
        public void doFilter(ServletRequest req, ServletResponse res, FilterChain chain)
                throws IOException, ServletException {
            HttpServletRequest request = (HttpServletRequest) req;
            String uri = request.getRequestURI();

            // 明确跳过的路径直接放行
            if (uri == null || uri.isEmpty() || uri.equals("/") || uri.equals("/index.html")) {
                chain.doFilter(req, res);
                return;
            }
            for (String prefix : SKIP_PREFIXES) {
                if (uri.startsWith(prefix)) {
                    chain.doFilter(req, res);
                    return;
                }
            }
            // 带扩展名的路径（静态资源）不走 SPA fallback
            int lastSlash = uri.lastIndexOf('/');
            String lastPart = (lastSlash >= 0) ? uri.substring(lastSlash + 1) : uri;
            int dot = lastPart.lastIndexOf('.');
            if (dot > 0 && dot < lastPart.length() - 1) {
                chain.doFilter(req, res);
                return;
            }

            // SPA 路由 → forward 到 index.html
            request.getRequestDispatcher("/index.html").forward(req, res);
        }
    }
}
