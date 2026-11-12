package com.lis.reagent.config;

import jakarta.servlet.*;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;

import java.io.IOException;

/**
 * SPA 路由回退：Vue Router history 模式下刷新 /reagent/inbound 等路径时，
 * 请求进入 Spring MVC 前直接 forward 到 /index.html，避免 404。
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
        registration.setOrder(Ordered.HIGHEST_PRECEDENCE);
        return registration;
    }

    static class RequestForwardFilter implements Filter {

        @Override
        public void doFilter(ServletRequest req, ServletResponse res, FilterChain chain)
                throws IOException, ServletException {
            HttpServletRequest request = (HttpServletRequest) req;
            String uri = request.getRequestURI();

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
            int lastSlash = uri.lastIndexOf('/');
            String lastPart = (lastSlash >= 0) ? uri.substring(lastSlash + 1) : uri;
            int dot = lastPart.lastIndexOf('.');
            if (dot > 0 && dot < lastPart.length() - 1) {
                chain.doFilter(req, res);
                return;
            }
            request.getRequestDispatcher("/index.html").forward(req, res);
        }
    }
}
