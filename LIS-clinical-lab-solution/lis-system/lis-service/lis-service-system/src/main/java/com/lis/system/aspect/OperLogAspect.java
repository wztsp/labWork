package com.lis.system.aspect;

import com.lis.common.annotation.OperLog;
import com.lis.common.security.SecurityContext;
import com.lis.system.entity.SysAuditLog;
import com.lis.system.service.SysAuditLogService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.lang.reflect.Method;
import java.time.LocalDateTime;
import java.util.Arrays;

/**
 * 操作日志切面 - 自动记录带@OperLog注解的方法调用
 */
@Slf4j
@Aspect
@Component
@RequiredArgsConstructor
public class OperLogAspect {

    private final SysAuditLogService sysAuditLogService;

    @Around("@annotation(com.lis.common.annotation.OperLog) || @within(com.lis.common.annotation.OperLog)")
    public Object around(ProceedingJoinPoint point) throws Throwable {
        long startTime = System.currentTimeMillis();

        MethodSignature signature = (MethodSignature) point.getSignature();
        Method method = signature.getMethod();
        Class<?> targetClass = point.getTarget().getClass();

        // 获取方法上的注解，若没有则获取类上的注解
        OperLog operLog = method.getAnnotation(OperLog.class);
        if (operLog == null) {
            operLog = targetClass.getAnnotation(OperLog.class);
        }

        SysAuditLog auditLog = buildAuditLog(operLog, method, targetClass);
        Object result = null;
        String resultStatus = "成功";

        try {
            if (operLog.recordParams()) {
                auditLog.setParams(Arrays.toString(point.getArgs()));
            }

            result = point.proceed();

            // 方法执行成功后，尝试获取用户信息（登录场景下用户信息可能在此时才可用）
            fillUserInfoIfAbsent(auditLog, result, point.getArgs());

            if (operLog.recordResult()) {
                auditLog.setResult(result != null ? result.toString() : "null");
            }
        } catch (Throwable e) {
            resultStatus = "失败: " + e.getMessage();
            auditLog.setResult(resultStatus);
            // 即使失败，也尝试获取用户信息
            fillUserInfoIfAbsent(auditLog, null, point.getArgs());
            throw e;
        } finally {
            long costTime = System.currentTimeMillis() - startTime;
            auditLog.setCostTime((int) costTime);
            auditLog.setResult(resultStatus);

            try {
                sysAuditLogService.record(auditLog);
                log.debug("操作日志已记录: module={}, operation={}, costTime={}ms",
                        auditLog.getModule(), auditLog.getOperation(), costTime);
            } catch (Exception e) {
                log.warn("记录操作日志失败: {}", e.getMessage());
            }
        }

        return result;
    }

    /**
     * 如果审计日志中缺少用户信息，尝试从 SecurityContext、返回值或参数中获取
     */
    private void fillUserInfoIfAbsent(SysAuditLog auditLog, Object result, Object[] args) {
        if (auditLog.getUsername() != null) {
            return; // 已有用户信息
        }

        // 1. 尝试从 SecurityContext 获取（适用于登录后调用的接口）
        SecurityContext.LoginUser loginUser = SecurityContext.get();
        if (loginUser != null) {
            auditLog.setUserId(loginUser.getUserId());
            auditLog.setUsername(loginUser.getUsername());
            return;
        }

        // 2. 尝试从返回值获取（适用于登录方法返回 LoginVO 的场景）
        if (result != null) {
            // 检查是否有 getUserId() 方法
            try {
                java.lang.reflect.Method getUserIdMethod = result.getClass().getMethod("getUserId");
                Object userId = getUserIdMethod.invoke(result);
                if (userId != null) {
                    auditLog.setUserId(Long.valueOf(userId.toString()));
                }
            } catch (NoSuchMethodException ignored) {
                // 忽略，继续尝试其他方式
            } catch (Exception e) {
                log.debug("从返回值获取userId失败: {}", e.getMessage());
            }

            // 检查是否有 getUsername() 方法
            if (auditLog.getUsername() == null) {
                try {
                    java.lang.reflect.Method getUsernameMethod = result.getClass().getMethod("getUsername");
                    Object username = getUsernameMethod.invoke(result);
                    if (username != null) {
                        auditLog.setUsername(username.toString());
                    }
                } catch (NoSuchMethodException ignored) {
                    // 忽略
                } catch (Exception e) {
                    log.debug("从返回值获取username失败: {}", e.getMessage());
                }
            }
        }

        // 3. 尝试从参数中获取用户名（适用于登录 DTO 场景）
        if (auditLog.getUsername() == null && args != null) {
            for (Object arg : args) {
                if (arg != null) {
                    try {
                        java.lang.reflect.Method getUsernameMethod = arg.getClass().getMethod("getUsername");
                        Object username = getUsernameMethod.invoke(arg);
                        if (username != null && !username.toString().isEmpty()) {
                            auditLog.setUsername(username.toString());
                            break;
                        }
                    } catch (NoSuchMethodException ignored) {
                        // 忽略
                    } catch (Exception e) {
                        // 忽略
                    }
                }
            }
        }
    }

    /**
     * 构建审计日志对象
     */
    private SysAuditLog buildAuditLog(OperLog operLog, Method method, Class<?> targetClass) {
        SysAuditLog auditLog = new SysAuditLog();

        // 设置模块和操作信息
        auditLog.setModule(operLog.module().isEmpty() ? targetClass.getSimpleName() : operLog.module());
        auditLog.setOperation(operLog.operation().isEmpty() ? method.getName() : operLog.operation());
        auditLog.setMethod(targetClass.getName() + "." + method.getName());

        // 设置用户信息
        SecurityContext.LoginUser loginUser = SecurityContext.get();
        if (loginUser != null) {
            auditLog.setUserId(loginUser.getUserId());
            auditLog.setUsername(loginUser.getUsername());
        }

        // 获取请求信息
        try {
            ServletRequestAttributes attributes = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
            if (attributes != null) {
                HttpServletRequest request = attributes.getRequest();
                auditLog.setIp(getClientIp(request));
            }
        } catch (Exception e) {
            log.warn("获取请求信息失败: {}", e.getMessage());
        }

        auditLog.setCreateTime(LocalDateTime.now());
        return auditLog;
    }

    /**
     * 获取客户端IP地址
     */
    private String getClientIp(HttpServletRequest request) {
        String ip = request.getHeader("X-Forwarded-For");
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getHeader("X-Real-IP");
        }
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getRemoteAddr();
        }
        // 处理代理IP（可能有多个，用逗号分隔）
        if (ip != null && ip.contains(",")) {
            ip = ip.split(",")[0].trim();
        }
        return ip;
    }
}
