package com.lis.common.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 操作日志注解 - 标记需要记录操作日志的方法
 * <p>
 * 使用示例:
 * <pre>
 * @OperLog(module = "用户管理", operation = "新增用户")
 * public void addUser(UserDTO dto) { ... }
 * </pre>
 */
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
public @interface OperLog {

    /**
     * 模块名称，如：用户管理、检验申请、报告审核等
     */
    String module() default "";

    /**
     * 操作描述，如：新增用户、删除申请、审核报告等
     */
    String operation() default "";

    /**
     * 是否需要记录请求参数
     */
    boolean recordParams() default false;

    /**
     * 是否需要记录返回结果
     */
    boolean recordResult() default false;
}
