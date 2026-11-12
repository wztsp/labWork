package com.lis.reagent.config;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.nio.charset.StandardCharsets;

/**
 * 登录校验：未登录访问页面跳转到 /login.html，访问 /api/** 返回 401 JSON。
 */
@Configuration
@RequiredArgsConstructor
public class AuthConfig implements WebMvcConfigurer {

    /** 会话中保存登录账号的 key */
    public static final String SESSION_USER = "LOGIN_USER";

    private final LoginProperties loginProperties;

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(new LoginInterceptor(loginProperties))
                .addPathPatterns("/**")
                .excludePathPatterns(
                        "/login.html",
                        "/api/auth/login",
                        "/api/auth/logout",
                        "/assets/**",
                        "/favicon.ico",
                        "/error");
    }

    static class LoginInterceptor implements HandlerInterceptor {

        private final LoginProperties props;

        LoginInterceptor(LoginProperties props) {
            this.props = props;
        }

        @Override
        public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler)
                throws Exception {
            if (!props.isEnabled()) {
                return true;
            }
            HttpSession session = request.getSession(false);
            if (session != null && session.getAttribute(SESSION_USER) != null) {
                return true;
            }

            if (request.getRequestURI().startsWith(request.getContextPath() + "/api/")) {
                response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                response.setCharacterEncoding(StandardCharsets.UTF_8.name());
                response.setContentType("application/json;charset=UTF-8");
                response.getWriter().write(
                        "{\"code\":401,\"message\":\"未登录或登录已过期，请重新登录\",\"data\":null}");
                return false;
            }
            response.sendRedirect(request.getContextPath() + "/login.html");
            return false;
        }
    }
}
