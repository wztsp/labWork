package com.lis.common.security;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 接口权限校验注解。
 * <p>
 * 标注在 Controller 方法或类上, 由 {@link PermissionInterceptor} 拦截校验。
 * 要求当前登录用户具备指定权限码(perm_code) 才允许访问, 否则返回 403。
 * 权限码见 sys_permission 表, 如 "system:user"、"system:role"、"system:perm"。
 */
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
public @interface RequirePermission {

    /** 需要的权限码, 对应 sys_permission.perm_code */
    String value();
}