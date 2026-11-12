package com.lis.common.security;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * 权限拦截器注册配置。
 * <p>
 * 位于 com.lis.common 包下, 经 RedisAutoConfiguration 的组件扫描
 * 自动注册到所有 Web 服务。拦截器仅对标注了 {@link RequirePermission}
 * 的接口生效, 其余接口不受影响。
 */
@Configuration
public class PermissionWebConfig implements WebMvcConfigurer {

    private final PermissionInterceptor permissionInterceptor;

    public PermissionWebConfig(PermissionInterceptor permissionInterceptor) {
        this.permissionInterceptor = permissionInterceptor;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(permissionInterceptor).addPathPatterns("/**");
    }
}